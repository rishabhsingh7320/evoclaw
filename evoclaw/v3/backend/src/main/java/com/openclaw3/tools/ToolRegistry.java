package com.openclaw3.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool> allTools = new LinkedHashMap<>();
    private final Map<String, Tool> activeTools = new LinkedHashMap<>();

    @Value("${tools.profile:full}")
    private String profile;

    @Value("${tools.allow:}")
    private String allowList;

    @Value("${tools.deny:}")
    private String denyList;

    private static final Map<String, Set<String>> PROFILES = Map.of(
        "full", Set.of("search", "file", "math", "http", "exec", "memory"),
        "minimal", Set.of("search", "file", "math"),
        "coding", Set.of("search", "file", "math", "exec", "memory")
    );

    public ToolRegistry(List<Tool> toolList) {
        for (Tool t : toolList) {
            allTools.put(t.getName(), t);
        }
    }

    @jakarta.annotation.PostConstruct
    public void applyPolicy() {
        activeTools.clear();

        Set<String> profileTools = PROFILES.getOrDefault(profile, PROFILES.get("full"));

        Set<String> allowed = new HashSet<>();
        if (allowList != null && !allowList.isBlank()) {
            allowed.addAll(Arrays.asList(allowList.split(",")));
        }

        Set<String> denied = new HashSet<>();
        if (denyList != null && !denyList.isBlank()) {
            denied.addAll(Arrays.asList(denyList.split(",")));
        }

        for (Map.Entry<String, Tool> entry : allTools.entrySet()) {
            String name = entry.getKey();

            if (!profileTools.contains(name)) continue;
            if (!allowed.isEmpty() && !allowed.contains(name)) continue;
            if (denied.contains(name)) continue;

            activeTools.put(name, entry.getValue());
        }

        log.info("Tool policy applied — profile={}, active tools: {}", profile, activeTools.keySet());
    }

    public Tool get(String name) {
        return activeTools.get(name);
    }

    public Map<String, Tool> all() {
        return activeTools;
    }

    public String buildToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (Tool t : activeTools.values()) {
            sb.append("- **").append(t.getName()).append("**: ").append(t.getDescription())
              .append("\n  Parameters: ").append(t.getParameterSchema()).append("\n");
        }
        return sb.toString();
    }

    public String getProfile() { return profile; }
    public Set<String> getActiveToolNames() { return new LinkedHashSet<>(activeTools.keySet()); }
}
