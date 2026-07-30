package org.opentron.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
// streaming helpers removed; no Consumer import needed

/**
 * OllamaCliService: Call Ollama via HTTP API for local models only.
 * No timeouts — virtual threads park and wait for completion.
 */
@Service
public class OllamaCliService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(OllamaCliService.class);
    private static final String OLLAMA_API = "http://127.0.0.1:11434/api/generate";
    private static final String OLLAMA_TAGS = "http://127.0.0.1:11434/api/tags";

    private boolean isCloudModel(String model) {
        if (model == null || model.isBlank()) return false;
        String lower = model.toLowerCase();
        return lower.startsWith("gpt-") || lower.startsWith("gpt4") ||
               lower.startsWith("claude-") || lower.startsWith("gemini-") ||
               lower.startsWith("openrouter/") || lower.startsWith("anthropic/");
    }

    @SuppressWarnings({ "deprecation", "unchecked" })
    private Map<String, Object> callOllama(String requestedModel, String promptStr) throws Exception {
        logger.info("Calling Ollama: {}", requestedModel);

        java.net.URL url = new java.net.URL(OLLAMA_API);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(0); // No timeout — virtual thread parks and waits
        conn.setDoOutput(true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", requestedModel);
        payload.put("prompt", promptStr);

        // Default sampling/generation settings
        payload.put("stream", false);
        payload.put("temperature", 0.7);
        payload.put("top_k", 40);
        payload.put("top_p", 0.9);
        payload.put("num_predict", 512);

        // Model-specific tuning: qwen3.5:9b has been observed to be significantly
        // slower on some hosts. Reduce generation size and narrow sampling for it
        // to improve latency for interactive use. Consider quantized variants
        // or GPU-backed inference for better throughput.
        if (requestedModel != null && requestedModel.toLowerCase().contains("qwen3.5")) {
            logger.info("Applying qwen3.5-specific tuning to reduce latency");
            payload.put("num_predict", 128);
            payload.put("temperature", 0.2);
            payload.put("top_k", 20);
            payload.put("top_p", 0.85);
        }

        String jsonPayload = objectMapper.writeValueAsString(payload);

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
        }

        logger.debug("Waiting for {}...", requestedModel);
        int responseCode = conn.getResponseCode();
        String responseText;

        if (responseCode == 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                responseText = sb.toString();
            }
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                responseText = sb.toString();
            }
            throw new RuntimeException("Ollama HTTP " + responseCode + ": " + responseText);
        }

        Map<String, Object> ollamaResp = objectMapper.readValue(responseText, java.util.HashMap.class);
        Object resp = ollamaResp.get("response");
        String responseContent = resp != null ? resp.toString().trim() : "";

        long promptTokens = ollamaResp.containsKey("prompt_eval_count")
            ? ((Number) ollamaResp.get("prompt_eval_count")).longValue() : 0;
        long completionTokens = ollamaResp.containsKey("eval_count")
            ? ((Number) ollamaResp.get("eval_count")).longValue() : 0;
        long totalTokens = promptTokens + completionTokens;

        logger.info("Done: {} chars, {} tokens", responseContent.length(), totalTokens);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", requestedModel);
        result.put("created", System.currentTimeMillis() / 1000);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", responseContent);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        result.put("choices", List.of(choice));

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", totalTokens);
        result.put("usage", usage);

        return result;
    }

    public Mono<Map<String, Object>> chatCompletion(String model, List<Map<String, String>> messages) {
        return Mono.fromCallable(() -> {
            if (isCloudModel(model)) {
                throw new RuntimeException("Cloud model '" + model + "' not supported via Ollama");
            }

            String requestedModel = model != null && !model.isBlank() ? model : "mistral";

            StringBuilder prompt = new StringBuilder();
            for (Map<String, String> msg : messages) {
                String role = msg.getOrDefault("role", "user");
                String content = msg.getOrDefault("content", "");
                if ("system".equals(role)) {
                    prompt.append("[System]: ").append(content).append("\n");
                } else if ("user".equals(role)) {
                    prompt.append(content);
                } else if ("assistant".equals(role)) {
                    prompt.append("\n[Assistant]: ").append(content);
                }
            }

            return callOllama(requestedModel, prompt.toString().trim());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // Streaming helper removed. Use the non-streaming `chatCompletion` method
    // for all Ollama interactions. If streaming is reintroduced later,
    // re-add a carefully tested `chatCompletionStream` implementation.

    @SuppressWarnings({ "unchecked", "deprecation" })
    public Mono<List<String>> listModels() {
        return (Mono<List<String>>) (Object) Mono.fromCallable(() -> {
            try {
                java.net.URL url = new java.net.URL(OLLAMA_TAGS);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) return List.of();

                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);

                    Map<String, Object> resp = objectMapper.readValue(sb.toString(), java.util.HashMap.class);
                    List<Map<String, Object>> models = (List<Map<String, Object>>) resp.get("models");

                    List<String> modelNames = new java.util.ArrayList<>();
                    if (models != null) {
                        for (Map<String, Object> m : models) {
                            Object name = m.get("name");
                            if (name != null) modelNames.add(name.toString());
                        }
                    }
                    return modelNames;
                }
            } catch (Exception e) {
                logger.warn("Error listing models", e);
                return List.of();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
