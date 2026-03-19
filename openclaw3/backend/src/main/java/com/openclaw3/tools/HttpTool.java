package com.openclaw3.tools;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class HttpTool implements Tool {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() { return "http"; }

    @Override
    public String getDescription() { return "Make an HTTP GET request to a KNOWN, WORKING URL. Only use this if you have an exact URL. For general information, use the 'search' tool instead."; }

    @Override
    public String getParameterSchema() { return "{ \"url\": \"string\" }"; }

    @Override
    public Map<String, Object> call(Map<String, Object> args) {
        String url = args != null ? String.valueOf(args.getOrDefault("url", "")) : "";
        Map<String, Object> res = new HashMap<>();
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (body != null && body.length() > 2000) {
                body = body.substring(0, 2000) + "\n... (truncated)";
            }
            res.put("success", true);
            res.put("output", body);
        } catch (Exception e) {
            res.put("success", false);
            res.put("error", e.getMessage());
        }
        return res;
    }
}
