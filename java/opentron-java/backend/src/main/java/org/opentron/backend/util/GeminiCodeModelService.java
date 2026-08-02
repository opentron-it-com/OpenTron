package org.opentron.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Gemini 2.0 Flash integration for code analysis.
 * Used as fallback for complex Backend/DevOps problems when local models fail.
 * Calls Gemini API REST endpoint directly using API key from request headers.
 */
@Service
public class GeminiCodeModelService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiCodeModelService.class);
    
    /**
     * Query Gemini 2.0 Flash for code analysis.
     * Uses Google API key from request headers.
     */
    public Map<String, Object> queryGeminiFlash(String systemPrompt, String userPrompt, Map<String, String> apiKeys) {
        Map<String, Object> result = new HashMap<>();
        
        String apiKey = apiKeys != null ? apiKeys.get("google") : null;
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("Google API key not provided in request headers");
            result.put("error", "GOOGLE_API_KEY not in request headers");
            result.put("status", "error");
            return result;
        }
        
        try {
            String geminiResponse = callGeminiFlashAPI(systemPrompt, userPrompt, apiKey);
            
            result.put("response", geminiResponse);
            result.put("model", "gemini-2.0-flash");
            result.put("status", "success");
            result.put("tokens_used", estimateTokens(userPrompt + geminiResponse));
            
            logger.info("Gemini Flash response completed - {} chars", geminiResponse.length());
            return result;
            
        } catch (Exception e) {
            logger.error("Gemini Flash error", e);
            result.put("error", e.getMessage());
            result.put("status", "error");
            return result;
        }
    }
    
    /**
     * Call Gemini 2.0 Flash REST API directly via HTTP POST.
     */
    private String callGeminiFlashAPI(String systemPrompt, String userPrompt, String apiKey) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey;
        
        String payload = buildGeminiPayload(systemPrompt, userPrompt);
        
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(60000);
        
        try (java.io.OutputStream os = conn.getOutputStream()) {
            byte[] input = payload.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        
        int status = conn.getResponseCode();
        StringBuilder response = new StringBuilder();
        
        if (status == 200) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            return parseGeminiResponse(response.toString());
        } else {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            throw new RuntimeException("Gemini API error: " + status + " - " + response);
        }
    }
    
    private String buildGeminiPayload(String systemPrompt, String userPrompt) {
        return String.format("""
            {
              "contents": [
                {
                  "role": "user",
                  "parts": [
                    {
                      "text": "%s\\n\\n%s"
                    }
                  ]
                }
              ],
              "systemInstruction": {
                "parts": [
                  {
                    "text": "%s"
                  }
                ]
              },
              "generationConfig": {
                "temperature": 0.7,
                "topK": 40,
                "topP": 0.95,
                "maxOutputTokens": 4096
              }
            }
            """, escapeJson(userPrompt), "", escapeJson(systemPrompt));
    }
    
    private String parseGeminiResponse(String jsonResponse) {
        try {
            int idx = jsonResponse.indexOf("\"text\":");
            if (idx != -1) {
                int start = jsonResponse.indexOf("\"", idx + 7) + 1;
                int end = jsonResponse.indexOf("\"", start);
                if (end > start) {
                    String text = jsonResponse.substring(start, end);
                    text = text.replace("\\n", "\n")
                               .replace("\\\"", "\"")
                               .replace("\\\\", "\\");
                    return text;
                }
            }
            return jsonResponse;
        } catch (Exception e) {
            logger.error("Failed to parse Gemini response", e);
            return jsonResponse;
        }
    }
    
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
    
    private long estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
    
    public boolean isAvailable(Map<String, String> apiKeys) {
        if (apiKeys == null) return false;
        String apiKey = apiKeys.get("google");
        return apiKey != null && !apiKey.isBlank();
    }
}
