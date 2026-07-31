package org.opentron.backend.tools;

import org.opentron.backend.services.TavilyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * TavilyTool - Registers Tavily search capabilities as an agent toolset.
 * Enables agents to perform real-time web searches and page extraction.
 */
@Service
public class TavilyTool {

    private static final Logger logger = LoggerFactory.getLogger(TavilyTool.class);
    private final TavilyService tavilyService;

    public TavilyTool(TavilyService tavilyService) {
        this.tavilyService = tavilyService;
    }

    /**
     * Get tool definition for agent toolsets.
     */
    public Map<String, Object> getToolDefinition() {
        return Map.ofEntries(
                Map.entry("name", "tavily"),
                Map.entry("description", "Real-time web search and page extraction via Tavily AI search API"),
                Map.entry("category", "search"),
                Map.entry("source", "builtin"),
                Map.entry("configured", tavilyService.isAvailable()),
                Map.entry("requires_credentials", true),
                Map.entry("credential_keys", List.of("TAVILY_API_KEY")),
                Map.entry("credential_help", "Set TAVILY_API_KEY environment variable with your API key from https://tavily.com/"),
                Map.entry("documentation_url", "https://tavily.com/docs"),
                Map.entry("examples", List.of(
                        "tavily search query='latest Docker AI agents' max_results=5",
                        "tavily search query='Spring Boot performance tuning' include_answer=true",
                        "tavily extract url='https://example.com/article'",
                        "tavily status"
                )),
                Map.entry("parameters", Map.of(
                        "search", Map.of(
                                "query", Map.of("type", "string", "description", "Search query"),
                                "max_results", Map.of("type", "integer", "description", "Max results (1-20)", "default", 5),
                                "include_answer", Map.of("type", "boolean", "description", "Include AI-generated answer", "default", false)
                        ),
                        "extract", Map.of(
                                "url", Map.of("type", "string", "description", "URL to extract content from")
                        )
                )),
                Map.entry("capabilities", List.of(
                        "web_search",
                        "ai_generated_answer",
                        "page_extraction",
                        "real_time_content",
                        "structured_results"
                )),
                Map.entry("rate_limits", Map.of(
                        "free_tier", "1000 searches/month",
                        "paid_tier", "custom limits"
                )),
                Map.entry("response_time_ms", "1000-3000"),
                Map.entry("cost_info", "Free tier available, paid plans start at $10/month")
        );
    }

    /**
     * Execute Tavily search command.
     * Format: tavily search query="your query" [max_results=5] [include_answer=false]
     */
    public Map<String, Object> search(String query, Integer maxResults, Boolean includeAnswer) {
        Map<String, Object> result = new HashMap<>();

        if (!tavilyService.isAvailable()) {
            result.put("status", "error");
            result.put("message", "Tavily service not configured. Set TAVILY_API_KEY environment variable.");
            logger.warn("Tavily search attempted but service not available");
            return result;
        }

        try {
            int limit = maxResults != null ? Math.min(maxResults, 20) : 5;
            boolean answer = includeAnswer != null && includeAnswer;

            List<Map<String, Object>> results = tavilyService.search(query, limit, answer);

            result.put("status", "success");
            result.put("query", query);
            result.put("result_count", results.size());
            result.put("results", results);
            result.put("timestamp", System.currentTimeMillis());

            logger.info("Tavily search completed for query: '{}' - {} results", query, results.size());

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Search failed: " + e.getMessage());
            logger.error("Tavily search error", e);
        }

        return result;
    }

    /**
     * Execute Tavily extract command.
     * Format: tavily extract url="https://example.com"
     */
    public Map<String, Object> extract(String url) {
        Map<String, Object> result = new HashMap<>();

        if (!tavilyService.isAvailable()) {
            result.put("status", "error");
            result.put("message", "Tavily service not configured. Set TAVILY_API_KEY environment variable.");
            logger.warn("Tavily extract attempted but service not available");
            return result;
        }

        try {
            Map<String, Object> extracted = tavilyService.extract(url);

            if (extracted.containsKey("error")) {
                result.put("status", "error");
                result.put("message", extracted.get("error"));
            } else {
                result.put("status", "success");
                result.put("url", url);
                result.putAll(extracted);
                result.put("timestamp", System.currentTimeMillis());
                logger.info("Tavily extraction completed for: {}", url);
            }

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Extraction failed: " + e.getMessage());
            logger.error("Tavily extract error", e);
        }

        return result;
    }

    /**
     * Get Tavily service status.
     */
    public Map<String, Object> getStatus() {
        return tavilyService.getStatus();
    }
}
