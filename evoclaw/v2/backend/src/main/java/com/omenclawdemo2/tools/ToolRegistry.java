package com.omenclawdemo2.tools;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool t : toolList) {
            tools.put(t.getName(), t);
        }
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Map<String, Tool> all() {
        return tools;
    }

    public String buildToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (Tool t : tools.values()) {
            sb.append("- **").append(t.getName()).append("**: ").append(t.getDescription())
              .append("\n  Parameters: ").append(t.getParameterSchema()).append("\n");
        }
        return sb.toString();
    }
}
