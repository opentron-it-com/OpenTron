package org.opentron.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Tavily Search Service - provides real-time web search and page extraction capabilities
 * for OpenTron agents. Tavily is an AI-native search API optimized for LLM integration.
 *
 * API Documentation: https://tavily.com/
 */
@Service
public class TavilyService {

    private static final Logger logger = LoggerFactory.getLogger(TavilyService.class);
    private static final String TAVILY_API_BASE = "https://api.tavily.com";
    private static final String SEARCH_ENDPOINT = "/search";
    private static final String EXTRACT_ENDPOINT = "/extract";

    @Value("${tavily.api-key:}")
    private String apiKey;

    @Value("${tavily.enabled:true}")
    private boolean enabled;

    @Value("${tavily.timeout:15000}")
    private int timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NetworkPolicyService networkPolicyService;

    public TavilyService(NetworkPolicyService networkPolicyService) {
        this.networkPolicyService = networkPolicyService;
    }

    /**
     * Perform a real-time web search via Tavily API.
     *
     * @param query          Search query
     * @param maxResults     Maximum number of results to return
     * @param includeAnswer  Include AI-generated answer from Tavily
     * @return List of search results with title, url, snippet, and metadata
     */
    public List<Map<String, Object>> search(String query, int maxResults, boolean includeAnswer) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (!isAvailable()) {
            logger.warn("Tavily service is not available or disabled");
            return results;
        }

        if (!networkPolicyService.isInternetAllowed()) {
            logger.info("Network policy blocks external searches");
            return results;
        }

        if (query == null || query.isBlank()) {
            logger.warn("Search query is empty");
            return results;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("api_key", apiKey);
            payload.put("query", query);
            payload.put("max_results", Math.min(maxResults, 20)); // Tavily max 20 per request
            payload.put("include_answer", includeAnswer);
            payload.put("include_raw_content", true);
            payload.put("topic", "general");
            payload.put("days", 7); // Last 7 days

            String response = makeRequest(SEARCH_ENDPOINT, payload);
            if (response == null) {
                logger.warn("No response from Tavily search");
                return results;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = objectMapper.readValue(response, Map.class);

            // Extract answer if requested
            String answer = (String) respMap.get("answer");
            if (answer != null && !answer.isBlank()) {
                Map<String, Object> answerResult = new HashMap<>();
                answerResult.put("type", "answer");
                answerResult.put("content", answer);
                results.add(answerResult);
                logger.debug("Tavily answer included: {} chars", answer.length());
            }

            // Extract search results
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tavilyResults = (List<Map<String, Object>>) respMap.get("results");

            if (tavilyResults != null) {
                for (Map<String, Object> result : tavilyResults) {
                    Map<String, Object> formatted = new HashMap<>();
                    formatted.put("type", "search_result");
                    formatted.put("title", result.getOrDefault("title", ""));
                    formatted.put("url", result.getOrDefault("url", ""));
                    formatted.put("snippet", result.getOrDefault("content", result.getOrDefault("snippet", "")));
                    formatted.put("score", result.getOrDefault("score", 0.0));

                    // Optional raw content
                    Object rawContent = result.get("raw_content");
                    if (rawContent != null) {
                        formatted.put("raw_content", rawContent);
                    }

                    results.add(formatted);
                }
                logger.debug("Tavily returned {} search results", tavilyResults.size());
            }

        } catch (Exception e) {
            logger.error("Tavily search failed for query: {}", query, e);
        }

        return results;
    }

    /**
     * Search with default parameters.
     */
    public List<Map<String, Object>> search(String query) {
        return search(query, 5, false);
    }

    /**
     * Extract and clean content from a specific URL.
     *
     * @param url The URL to extract content from
     * @return Cleaned, structured content from the page
     */
    public Map<String, Object> extract(String url) {
        Map<String, Object> result = new HashMap<>();

        if (!isAvailable()) {
            logger.warn("Tavily service is not available or disabled");
            result.put("error", "Tavily service unavailable");
            return result;
        }

        if (!networkPolicyService.isInternetAllowed()) {
            logger.info("Network policy blocks external extractions");
            result.put("error", "Network policy blocks external access");
            return result;
        }

        if (url == null || url.isBlank()) {
            logger.warn("Extract URL is empty");
            result.put("error", "URL is empty");
            return result;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("api_key", apiKey);
            payload.put("urls", List.of(url));

            String response = makeRequest(EXTRACT_ENDPOINT, payload);
            if (response == null) {
                logger.warn("No response from Tavily extract");
                result.put("error", "No response from Tavily");
                return result;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = objectMapper.readValue(response, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> extractedData = (Map<String, Object>) respMap.get(url);
            if (extractedData != null) {
                result.putAll(extractedData);
                logger.debug("Tavily extracted content from: {}", url);
            } else {
                result.put("error", "No extraction result for URL");
            }

        } catch (Exception e) {
            logger.error("Tavily extract failed for URL: {}", url, e);
            result.put("error", "Extraction failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Check if Tavily service is properly configured and enabled.
     */
    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Get Tavily service status for monitoring/diagnostics.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "Tavily Search");
        status.put("enabled", enabled);
        status.put("configured", isAvailable());
        status.put("api_base", TAVILY_API_BASE);
        status.put("timeout_ms", timeout);
        status.put("network_allowed", networkPolicyService.isInternetAllowed());
        return status;
    }

    /**
     * Make HTTP POST request to Tavily API.
     */
    private String makeRequest(String endpoint, Map<String, Object> payload) throws Exception {
        String url = TAVILY_API_BASE + endpoint;
        java.net.URL u = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "OpenTron/0.1.0");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setDoOutput(true);

        // Write payload
        String jsonPayload = objectMapper.writeValueAsString(payload);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            logger.warn("Tavily API returned status code: {}", responseCode);
            String errorBody = readErrorStream(conn);
            logger.debug("Error response: {}", errorBody);
            return null;
        }

        StringBuilder responseBody = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                responseBody.append(line);
            }
        }

        return responseBody.toString();
    }

    private String readErrorStream(java.net.HttpURLConnection conn) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error reading error stream: " + e.getMessage();
        }
    }
}
