package com.openclawdemo.orchestrator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SearchTool implements Tool {

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public Map<String, Object> call(Map<String, Object> args) {
        String query = args != null ? String.valueOf(args.getOrDefault("query", "")) : "";
        Map<String, Object> result = new HashMap<>();
        result.put("summary", "Fake search results for: " + query);
        return result;
    }
}

