package org.opentron.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentron.backend.services.SearchKeyService;
import org.opentron.backend.services.NetworkPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebSearchService {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchService.class);
    private final SearchKeyService searchKeyService;
    private final NetworkPolicyService networkPolicyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSearchService(SearchKeyService searchKeyService, NetworkPolicyService networkPolicyService) {
        this.searchKeyService = searchKeyService;
        this.networkPolicyService = networkPolicyService;
    }

    /**
     * Perform a simple web search via SerpAPI. Returns a list of result maps: {title, link, snippet}.
     */
    public List<Map<String, String>> search(String query, int limit) {
        return search(query, limit, null);
    }

    public List<Map<String, String>> search(String query, int limit, String provider) {
        List<Map<String, String>> out = new ArrayList<>();
        if (query == null || query.isBlank()) return out;
        if (!networkPolicyService.isInternetAllowed()) {
            logger.info("Network policy blocks external searches");
            return out;
        }
        String apiKey = searchKeyService.loadKey();
        String configuredProvider = provider == null ? searchKeyService.loadProvider() : provider;
        if (configuredProvider == null) configuredProvider = "serpapi";

        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("No search API key configured");
            return out;
        }

        try {
            String qs = URLEncoder.encode(query, StandardCharsets.UTF_8);
            if ("serpapi".equalsIgnoreCase(configuredProvider)) {
                String url = "https://serpapi.com/search.json?q=" + qs + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8) + "&num=" + limit;
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                int code = conn.getResponseCode();
                if (code != 200) {
                    logger.warn("SerpAPI returned {}", code);
                    return out;
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = objectMapper.readValue(sb.toString(), Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) resp.get("organic_results");
                if (results == null) return out;
                int added = 0;
                for (Map<String, Object> r : results) {
                    if (added >= limit) break;
                    String title = r.getOrDefault("title", "").toString();
                    String link = r.getOrDefault("link", "").toString();
                    String snippet = r.getOrDefault("snippet", "").toString();
                    Map<String, String> row = new HashMap<>();
                    row.put("title", title);
                    row.put("link", link);
                    row.put("snippet", snippet);
                    out.add(row);
                    added++;
                }
            } else if ("tavily".equalsIgnoreCase(configuredProvider)) {
                // Tavily: attempt to call Tavily public API (best-effort)
                // Note: Tavily API formats may differ; adjust if you have a specific endpoint.
                String url = "https://api.tavily.com/v1/search?q=" + qs;
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                int code = conn.getResponseCode();
                if (code != 200) {
                    logger.warn("Tavily search returned {}", code);
                    return out;
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = objectMapper.readValue(sb.toString(), Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) resp.get("results");
                if (results == null) return out;
                int added = 0;
                for (Map<String, Object> r : results) {
                    if (added >= limit) break;
                    String title = r.getOrDefault("title", "").toString();
                    String link = r.getOrDefault("url", r.getOrDefault("link", "")).toString();
                    String snippet = r.getOrDefault("snippet", r.getOrDefault("summary", "")).toString();
                    Map<String, String> row = new HashMap<>();
                    row.put("title", title);
                    row.put("link", link);
                    row.put("snippet", snippet);
                    out.add(row);
                    added++;
                }
            } else {
                logger.warn("Unknown search provider: {}", configuredProvider);
            }
        } catch (Exception e) {
            logger.warn("Web search failed", e);
        }
        return out;
    }
}
