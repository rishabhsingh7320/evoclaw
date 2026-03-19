package com.openclaw4.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;

@Component
public class LlmClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
    }

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

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/v1/chat/completions",
                    HttpMethod.POST,
                    request,
                    String.class
            );
            JsonNode root = mapper.readTree(response.getBody());
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (ResourceAccessException e) {
            String msg = e.getMessage() != null && (e.getMessage().contains("Read timed out") || e.getMessage().contains("Connection timed out"))
                    ? "LLM API timed out (planning or execution took too long). Please try again or use a shorter query."
                    : "LLM API connection failed: " + e.getMessage();
            throw new RuntimeException(msg, e);
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            String msg = status == 401
                    ? "LLM API authentication failed (401). Check that OPENAI_API_KEY is set and valid when starting the server."
                    : "LLM API error (" + status + "): " + (responseBody != null && !responseBody.isBlank() ? responseBody : e.getMessage());
            throw new RuntimeException(msg, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call LLM: " + e.getMessage(), e);
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
