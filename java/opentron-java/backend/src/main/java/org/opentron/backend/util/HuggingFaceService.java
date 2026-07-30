package org.opentron.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

/**
 * HuggingFaceService: Integrate with HF Inference API or local HF models for fast inference.
 * Supports both cloud API and local models (via transformers + FastAPI).
 */
@Service
public class HuggingFaceService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceService.class);
    
    // Configuration - use environment variables or fallback to defaults
    private static final String HF_API_TOKEN = System.getenv("HF_API_TOKEN") != null 
        ? System.getenv("HF_API_TOKEN") 
        : ""; // Get from https://huggingface.co/settings/tokens
    
    private static final String HF_MODE = System.getenv("HF_MODE") != null
        ? System.getenv("HF_MODE")
        : "local"; // "local" or "api"
    
    private static final String HF_LOCAL_URL = System.getenv("HF_LOCAL_URL") != null
        ? System.getenv("HF_LOCAL_URL")
        : "http://127.0.0.1:8000"; // Local HF server via FastAPI
    
    private static final String HF_API_URL = "https://api-inference.huggingface.co/models";
    
    // Default fast model for local: Mistral 7B (same speed as Ollama mistral but better quality)
    private static final String DEFAULT_LOCAL_MODEL = "mistralai/Mistral-7B-Instruct-v0.1";
    
    // Fast cloud models: prefer smaller, faster models
    private static final String DEFAULT_CLOUD_MODEL = "mistralai/Mistral-7B-Instruct-v0.1";

    /**
     * Chat completion via Hugging Face (cloud or local)
     */
    public Mono<Map<String, Object>> chatCompletion(String model, List<Map<String, String>> messages) {
        return Mono.fromCallable(() -> {
            logger.info("HuggingFace chat completion: mode={} model={}", HF_MODE, model);
            
            if ("api".equalsIgnoreCase(HF_MODE)) {
                return chatCompletionViaApi(model, messages);
            } else {
                return chatCompletionLocal(model, messages);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Chat via HF Inference API (cloud)
     */
    @SuppressWarnings("deprecation")
    private Map<String, Object> chatCompletionViaApi(String model, List<Map<String, String>> messages) throws Exception {
        if (HF_API_TOKEN == null || HF_API_TOKEN.isEmpty()) {
            throw new RuntimeException("HF_API_TOKEN not set. Get one from https://huggingface.co/settings/tokens");
        }

        String actualModel = model != null && !model.isEmpty() ? model : DEFAULT_CLOUD_MODEL;
        logger.info("Using HF API with model: {}", actualModel);

        // Build request
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("inputs", formatMessagesForHF(messages));
        requestPayload.put("parameters", Map.of(
            "max_new_tokens", 256,
            "temperature", 0.7,
            "top_p", 0.95
        ));

        String url = HF_API_URL + "/" + actualModel;
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + HF_API_TOKEN);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(120000); // Cloud can be slower
        conn.setDoOutput(true);

        String jsonPayload = objectMapper.writeValueAsString(requestPayload);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        String responseText = readResponse(conn, responseCode);
        
        if (responseCode != 200) {
            logger.error("HF API error {}: {}", responseCode, responseText);
            throw new RuntimeException("HF API error " + responseCode + ": " + responseText);
        }

        return parseHFApiResponse(actualModel, responseText, messages);
    }

    /**
     * Chat via local HF server (FastAPI)
     */
    @SuppressWarnings("deprecation")
    private Map<String, Object> chatCompletionLocal(String model, List<Map<String, String>> messages) throws Exception {
        String actualModel = model != null && !model.isEmpty() ? model : DEFAULT_LOCAL_MODEL;
        logger.info("Using local HF with model: {}", actualModel);

        // Build request for local FastAPI server
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("model", actualModel);
        requestPayload.put("messages", messages);
        requestPayload.put("temperature", 0.7);
        requestPayload.put("max_tokens", 256);

        String url = HF_LOCAL_URL + "/v1/chat/completions"; // Compatible with OpenAI format
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(60000); // Local should be fast
        conn.setDoOutput(true);

        String jsonPayload = objectMapper.writeValueAsString(requestPayload);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        String responseText = readResponse(conn, responseCode);

        if (responseCode != 200) {
            logger.error("Local HF error {}: {}", responseCode, responseText);
            throw new RuntimeException("Local HF error " + responseCode + ": " + responseText);
        }

        return parseLocalResponse(actualModel, responseText);
    }

    /**
     * Parse HF API response
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseHFApiResponse(String model, String responseText, List<Map<String, String>> messages) throws Exception {
        List<Map<String, Object>> apiResp = objectMapper.readValue(responseText, List.class);
        
        if (apiResp == null || apiResp.isEmpty()) {
            throw new RuntimeException("Empty response from HF API");
        }

        String content = (String) apiResp.get(0).get("generated_text");
        if (content == null) {
            content = "No response generated";
        }

        // Rough token estimation (HF API doesn't return token counts)
        long promptTokens = estimateTokens(formatMessagesForHF(messages));
        long completionTokens = estimateTokens(content);
        long totalTokens = promptTokens + completionTokens;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", model);
        result.put("created", System.currentTimeMillis() / 1000);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);

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

    /**
     * Parse local HF response (OpenAI format)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseLocalResponse(String model, String responseText) throws Exception {
        Map<String, Object> resp = objectMapper.readValue(responseText, Map.class);
        
        // Already in OpenAI format, just ensure usage is present
        if (!resp.containsKey("usage")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                String content = message != null ? (String) message.get("content") : "";
                
                Map<String, Object> usage = new LinkedHashMap<>();
                usage.put("prompt_tokens", 100); // Estimate
                usage.put("completion_tokens", (int)(content.length() / 4));
                usage.put("total_tokens", (int)(100 + content.length() / 4));
                resp.put("usage", usage);
            }
        }
        
        return resp;
    }

    /**
     * Format messages for HF API (convert from OpenAI format)
     */
    private String formatMessagesForHF(List<Map<String, String>> messages) {
        StringBuilder prompt = new StringBuilder();
        for (Map<String, String> msg : messages) {
            String role = msg.getOrDefault("role", "user");
            String content = msg.getOrDefault("content", "");
            
            if ("user".equals(role)) {
                prompt.append("[INST] ").append(content).append(" [/INST]");
            } else if ("assistant".equals(role)) {
                prompt.append(" ").append(content).append(" ");
            }
        }
        return prompt.toString();
    }

    /**
     * Estimate tokens (rough: ~4 chars per token)
     */
    private long estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }

    /**
     * Read HTTP response
     */
    private String readResponse(java.net.HttpURLConnection conn, int responseCode) throws Exception {
        java.io.InputStream is = responseCode == 200 ? conn.getInputStream() : conn.getErrorStream();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * List available models (from local cache or HF Hub)
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
    public Mono<List<String>> listModels() {
        return Mono.fromCallable(() -> {
            logger.info("Listing available models (mode: {})", HF_MODE);
            
            List<String> models = new ArrayList<>();
            
            if ("api".equalsIgnoreCase(HF_MODE)) {
                // Cloud: return curated fast models
                models.addAll(Arrays.asList(
                    "mistralai/Mistral-7B-Instruct-v0.1",
                    "meta-llama/Llama-2-7b-chat-hf",
                    "NousResearch/Nous-Hermes-2-Mistral-7B-DPO",
                    "DiscoResearch/DiscoLM_German_7b_v1",
                    "gpt2"
                ));
            } else {
                // Local: check what's in the local server
                try {
                    String url = HF_LOCAL_URL + "/models";
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    
                    if (conn.getResponseCode() == 200) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                            Map<String, Object> resp = objectMapper.readValue(sb.toString(), Map.class);
                            if (resp.containsKey("models")) {
                                models.addAll((List<String>) resp.get("models"));
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Could not fetch local models", e);
                }
                
                // Add defaults if list is empty
                if (models.isEmpty()) {
                    models.addAll(Arrays.asList(DEFAULT_LOCAL_MODEL));
                }
            }
            
            return models;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
