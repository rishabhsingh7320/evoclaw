package com.omenclawdemo2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LlmClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    public String chat(String systemPrompt, String userPrompt) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.2);

        ArrayNode messages = body.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", userPrompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(mapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize LLM request", e);
        }

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/v1/chat/completions",
                HttpMethod.POST,
                request,
                String.class
        );

        try {
            JsonNode root = mapper.readTree(response.getBody());
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM response: " + response.getBody(), e);
        }
    }

    public String extractJson(String content) {
        if (content == null) return "{}";
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }
}
