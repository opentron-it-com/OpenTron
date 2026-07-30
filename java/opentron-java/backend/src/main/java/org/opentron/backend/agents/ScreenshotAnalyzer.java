package org.opentron.backend.agents;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * ScreenshotAnalyzer - Uses LLaVA vision model to analyze screenshots
 */
@Service
public class ScreenshotAnalyzer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String LLAVA_MODEL = "llava";
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotAnalyzer.class);

    /**
     * Analyze a screenshot using LLaVA vision model via HTTP API
     */
    public Map<String, Object> analyzeScreenshot(String imageBase64, String question, String context) {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Analyzing screenshot with LLaVA");
            
            // Remove data URI prefix if present
            if (imageBase64.startsWith("data:")) {
                imageBase64 = imageBase64.substring(imageBase64.indexOf(",") + 1);
            }

            String fullQuestion = question + (context.isBlank() ? "" : "\n\nContext: " + context);

            // Call LLaVA directly (synchronous, will wait up to 5 minutes)
            Map<String, Object> response = callLLaVAAPI(imageBase64, fullQuestion, 300);
            
            if (response == null) {
                response = new HashMap<>();
            } else {
                response = new HashMap<>(response);
            }
            
            String content = "";
            
            // LLaVA returns message directly, extract it
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) response.get("message");
            if (message != null) {
                content = (String) message.get("content");
                logger.debug("Extracted content from message");
            } else {
                // Fallback to choices format (OpenAI compatibility)
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                    content = msg != null ? (String) msg.get("content") : "";
                    logger.debug("Extracted content from choices");
                }
            }
            
            if (content != null && !content.isEmpty()) {
                List<String> suggestions = parseSuggestions(content);
                
                Map<String, Object> result = new HashMap<>();
                result.put("status", "completed");
                result.put("analysis", content);
                result.put("suggestions", suggestions);
                result.put("model", LLAVA_MODEL);
                
                long elapsed = System.currentTimeMillis() - startTime;
                result.put("elapsed_ms", elapsed);
                logger.info("Analysis complete in {}ms", elapsed);
                return result;
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            response.put("status", "error");
            response.put("error", "No content extracted from LLaVA response");
            response.put("elapsed_ms", elapsed);
            return response;

        } catch (Exception e) {
            logger.error("Error analyzing screenshot", e);
            long elapsed = System.currentTimeMillis() - startTime;
            return Map.of(
                "status", "error",
                "error", e.getMessage() != null ? e.getMessage() : "Unknown error",
                "elapsed_ms", elapsed
            );
        }
    }

    /**
     * Call Ollama LLaVA API directly
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callLLaVAAPI(String imageBase64, String question, int timeoutSeconds) throws Exception {
        @SuppressWarnings("deprecation")
        java.net.URL url = new java.net.URL("http://127.0.0.1:11434/api/chat");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(timeoutSeconds * 1000);
        conn.setDoOutput(true);

        // Build request with image
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", question);
        userMsg.put("images", List.of(imageBase64));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", LLAVA_MODEL);
        payload.put("messages", List.of(userMsg));
        payload.put("stream", false);
        payload.put("temperature", 0.7);

        String jsonPayload = objectMapper.writeValueAsString(payload);
        
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                logger.warn("LLaVA error: {}", sb.toString());
            }
            throw new RuntimeException("LLaVA HTTP error " + responseCode);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return objectMapper.readValue(sb.toString(), java.util.HashMap.class);
        }
    }

    /**
     * Parse suggestions from LLaVA response
     */
    private List<String> parseSuggestions(String content) {
        List<String> suggestions = new ArrayList<>();
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^[-•*\\d+.]+\\s+.*")) {
                String suggestion = trimmed.replaceAll("^[-•*\\d+.]+\\s+", "").trim();
                if (!suggestion.isEmpty() && suggestion.length() > 5) {
                    suggestions.add(suggestion);
                }
            }
        }

        if (suggestions.isEmpty()) {
            String[] sentences = content.split("[.!?]+");
            for (String sentence : sentences) {
                String s = sentence.trim();
                if (!s.isEmpty() && s.length() > 15) {
                    suggestions.add(s + ".");
                }
            }
        }

        return suggestions.isEmpty() ? List.of(content) : suggestions;
    }
}
