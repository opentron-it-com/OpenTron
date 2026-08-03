package org.opentron.backend.agents;

import org.opentron.backend.util.CloudModelService;
import org.opentron.backend.services.WebSearchService;
import org.opentron.backend.services.NetworkPolicyService;
import org.opentron.backend.util.OllamaCliService;
import org.opentron.backend.util.HuggingFaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AgentLLMBridge - Single clean call to Ollama, no retries, no timeouts.
 * Virtual threads handle the wait; only fails if Ollama is genuinely down.
 * 
 * Cloud model failures automatically fall back to local Ollama.
 */
public class AgentLLMBridge {

    private final OllamaCliService ollamaService;
    private final HuggingFaceService huggingFaceService;
    private final CloudModelService cloudModelService;
    private final String model;
    private final boolean useHF;
    private final Map<String, String> apiKeyOverrides;
    private final WebSearchService webSearchService;
    private static final Logger logger = LoggerFactory.getLogger(AgentLLMBridge.class);

    public AgentLLMBridge(OllamaCliService ollamaService, HuggingFaceService huggingFaceService, String model) {
        this(ollamaService, huggingFaceService, null, model, null);
    }

    public AgentLLMBridge(OllamaCliService ollamaService, HuggingFaceService huggingFaceService, CloudModelService cloudModelService,
                          String model, Map<String, String> apiKeyOverrides) {
        this.ollamaService = ollamaService;
        this.huggingFaceService = huggingFaceService;
        this.cloudModelService = cloudModelService;
        this.model = model != null ? model : "mistral";
        this.useHF = System.getenv("HF_MODE") != null &&
                    ("local".equalsIgnoreCase(System.getenv("HF_MODE")) ||
                     "api".equalsIgnoreCase(System.getenv("HF_MODE")));
        this.apiKeyOverrides = apiKeyOverrides == null ? Collections.emptyMap() : apiKeyOverrides;
        this.webSearchService = null;
    }

    public AgentLLMBridge(OllamaCliService ollamaService, HuggingFaceService huggingFaceService, CloudModelService cloudModelService,
                          String model, Map<String, String> apiKeyOverrides, WebSearchService webSearchService) {
        this.ollamaService = ollamaService;
        this.huggingFaceService = huggingFaceService;
        this.cloudModelService = cloudModelService;
        this.model = model != null ? model : "mistral";
        this.useHF = System.getenv("HF_MODE") != null &&
                    ("local".equalsIgnoreCase(System.getenv("HF_MODE")) ||
                     "api".equalsIgnoreCase(System.getenv("HF_MODE")));
        this.apiKeyOverrides = apiKeyOverrides == null ? Collections.emptyMap() : apiKeyOverrides;
        this.webSearchService = webSearchService;
    }

    /**
     * Query LLM — single attempt, waits indefinitely (virtual thread parks).
     * Only returns error if Ollama is unreachable or returns HTTP error.
     */
    public Map<String, Object> queryLLM(String systemPrompt, String userQuestion, int maxTokens) {
        try {
            logger.info("Querying {}...", model);

            // Prepend current server date/time to the system prompt so models answer time questions correctly.
            String now = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));
            if (systemPrompt == null) systemPrompt = "";
            String timestampPrefix = "Current server date/time: " + now + "\n\n";

            // If a web search service is available and system prompt suggests internet research,
            // perform a quick search and include top results in the system prompt to augment context.
            String augmentedSystem = systemPrompt;
            try {
                if (webSearchService != null && systemPrompt != null && systemPrompt.toLowerCase().contains("internet")) {
                    var results = webSearchService.search(userQuestion, 3);
                    if (results != null && !results.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("\n\nSearch results (top " + results.size() + "):\n");
                        for (var r : results) {
                            String title = r.getOrDefault("title", "");
                            String link = r.getOrDefault("link", "");
                            String snippet = r.getOrDefault("snippet", "");
                            if (snippet.length() > 200) snippet = snippet.substring(0,200) + "...";
                            sb.append("- ").append(title).append(" (").append(link).append("): ").append(snippet).append("\n");
                        }
                        augmentedSystem = systemPrompt + sb.toString();
                    }
                }
            } catch (Exception e) {
                logger.warn("WebSearch augmentation failed", e);
            }

            // Always prepend timestamp prefix so models have an authoritative current time
            augmentedSystem = timestampPrefix + augmentedSystem;

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", augmentedSystem));
            messages.add(Map.of("role", "user", "content", userQuestion));

            Map<String, Object> response = invokeModel(messages);

            if (response == null) {
                return errorResponse("LLM returned null response");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return errorResponse("Empty choices in Ollama response");
            }

            Map<String, Object> choice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            String content = message != null ? (String) message.get("content") : "";

            if (content == null || content.isBlank()) {
                return errorResponse("Empty content from LLM");
            }

            logger.info("{} responded: {} chars", model, content.length());
            return parseRecommendations(content, response);

        } catch (Exception e) {
            logger.error("Error querying LLM", e);
            return errorResponse("LLM unavailable: " + e.getMessage());
        }
    }

    /**
     * Stream-aware variant: invokes the underlying Ollama service in streaming
     * mode and calls `onChunk` for each partial chunk. Blocks until complete
     * and returns a final structured response map.
     */
    public Map<String, Object> queryLLMStream(String systemPrompt, String userQuestion, int maxTokens, Consumer<String> onChunk) {
        // Streaming is currently disabled / reverted. Use the non-streaming
        // queryLLM path and return a single final response. This keeps the
        // coordinator and frontend behavior simple and avoids partial
        // chunking bugs until we reintroduce a robust streaming UX.
        try {
            logger.info("Streaming disabled - using non-streaming path for {}", model);
            return queryLLM(systemPrompt, userQuestion, maxTokens);
        } catch (Exception e) {
            logger.error("Error querying LLM (stream fallback)", e);
            return errorResponse("LLM unavailable: " + e.getMessage());
        }
    }

    private boolean isCloudModel(String modelName) {
        if (modelName == null || modelName.isBlank()) return false;
        String lower = modelName.toLowerCase();
        return lower.startsWith("gpt-") || lower.startsWith("gpt4") || lower.startsWith("o1-") ||
               lower.startsWith("o3-") || lower.startsWith("o4-") || lower.startsWith("chatgpt-") ||
               lower.startsWith("claude-") || lower.startsWith("gemini-") ||
               lower.startsWith("openrouter/") || lower.startsWith("anthropic/") || lower.startsWith("minimax-");
    }

    private Map<String, Object> invokeModel(List<Map<String, String>> messages) {
        if (isCloudModel(model)) {
            if (cloudModelService == null) {
                throw new IllegalStateException("Cloud model service not configured for model: " + model);
            }
            try {
                logger.info("Routing cloud model {} through CloudModelService", model);
                return cloudModelService.callCloudModel(model, messages, apiKeyOverrides).block();
            } catch (Exception e) {
                logger.warn("Cloud model {} failed ({}), falling back to Ollama llama3.2:3b", model, e.getMessage());
                // Fall back to local model when cloud fails
                return ollamaService.chatCompletion("llama3.2:3b", messages).block();
            }
        }

        if (useHF) {
            return huggingFaceService.chatCompletion(model, messages).block();
        }

        return ollamaService.chatCompletion(model, messages).block();
    }

    private Map<String, Object> parseRecommendations(String content, Map<String, Object> response) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "completed");
        result.put("response", content);

        List<String> recommendations = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^[-•*\\d+.]+\\s+.*")) {
                String rec = trimmed.replaceAll("^[-•*\\d+.]+\\s+", "").trim();
                if (!rec.isEmpty()) recommendations.add(rec);
            }
        }
        if (recommendations.isEmpty()) {
            for (String sentence : content.split("[.!?]+")) {
                String s = sentence.trim();
                if (s.length() > 10) recommendations.add(s);
            }
        }
        result.put("recommendations", recommendations);

        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        if (usage != null) {
            result.put("tokens_used", usage.get("total_tokens"));
        }

        return result;
    }

    private Map<String, Object> errorResponse(String error) {
        return Map.of("status", "error", "error", error, "recommendations", List.of());
    }
}
