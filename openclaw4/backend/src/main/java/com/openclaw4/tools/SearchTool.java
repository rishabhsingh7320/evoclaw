package com.openclaw4.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Web search tool powered by the Tavily Search API (https://tavily.com).
 * Falls back to a basic mock if no API key is configured.
 *
 * Tavily free tier: 1000 searches/month.
 * Get a key at: https://app.tavily.com/sign-in
 */
@Component
public class SearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SearchTool.class);
    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${search.tavily-api-key:}")
    private String tavilyApiKey;

    @Value("${search.provider:tavily}")
    private String provider;

    @Override
    public String getName() { return "search"; }

    @Override
    public String getDescription() {
        return "Search the web for real-time information on ANY topic: weather, news, facts, events, prices, research. This is your primary information source — always try this first.";
    }

    @Override
    public String getParameterSchema() { return "{ \"query\": \"string (the search query)\" }"; }

    @Override
    public Map<String, Object> call(Map<String, Object> args) {
        String query = args != null ? String.valueOf(args.getOrDefault("query", "")) : "";
        Map<String, Object> result = new HashMap<>();

        if (isRealSearchAvailable()) {
            return tavilySearch(query);
        } else {
            log.warn("No Tavily API key configured — using mock search. Set TAVILY_API_KEY for real results.");
            return mockSearch(query);
        }
    }

    private boolean isRealSearchAvailable() {
        return "tavily".equalsIgnoreCase(provider)
                && tavilyApiKey != null
                && !tavilyApiKey.isBlank();
    }

    // ── Tavily Search ───────────────────────────────────────────────────────

    private Map<String, Object> tavilySearch(String query) {
        Map<String, Object> result = new HashMap<>();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("api_key", tavilyApiKey);
            body.put("query", query);
            body.put("search_depth", "basic");
            body.put("include_answer", true);
            body.put("max_results", 5);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(mapper.writeValueAsString(body), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    TAVILY_URL, HttpMethod.POST, request, String.class
            );

            JsonNode root = mapper.readTree(response.getBody());

            StringBuilder output = new StringBuilder();

            // Tavily returns a direct "answer" field — the best summary
            String answer = root.path("answer").asText("");
            if (!answer.isBlank()) {
                output.append("Answer: ").append(answer).append("\n\n");
            }

            // Also include individual search results
            JsonNode results = root.path("results");
            if (results.isArray()) {
                output.append("Sources:\n");
                int i = 1;
                for (JsonNode r : results) {
                    String title = r.path("title").asText("");
                    String content = r.path("content").asText("");
                    String url = r.path("url").asText("");
                    if (!title.isBlank()) {
                        output.append(i).append(". ").append(title).append("\n");
                        if (!content.isBlank()) {
                            // Truncate long content
                            String snippet = content.length() > 300
                                    ? content.substring(0, 300) + "..."
                                    : content;
                            output.append("   ").append(snippet).append("\n");
                        }
                        if (!url.isBlank()) {
                            output.append("   URL: ").append(url).append("\n");
                        }
                        output.append("\n");
                        i++;
                    }
                }
            }

            String outputStr = output.toString().trim();
            if (outputStr.isEmpty()) {
                outputStr = "No results found for: " + query;
            }

            result.put("success", true);
            result.put("output", outputStr);
            result.put("source", "tavily");

        } catch (Exception e) {
            log.error("Tavily search failed for query '{}': {}", query, e.getMessage());
            result.put("success", false);
            result.put("error", "Search failed: " + e.getMessage());
            result.put("source", "tavily");
        }
        return result;
    }

    // ── Mock Fallback ───────────────────────────────────────────────────────

    private Map<String, Object> mockSearch(String query) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("source", "mock");
        result.put("output", "Search results for '" + query + "':\n"
                + "1. Comprehensive overview: " + query + " — detailed analysis from multiple authoritative sources.\n"
                + "2. Key findings: Significant developments have been reported recently.\n"
                + "3. Expert analysis: Multiple perspectives available with supporting data.\n"
                + "4. Related topics and in-depth articles found.\n"
                + "5. Community discussions and official statements available.\n"
                + "\n[NOTE: These are mock results. Set TAVILY_API_KEY env variable for real web search.]");
        return result;
    }
}
