package com.openclawdemo.orchestrator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(SearchTool searchTool, FileTool fileTool, MathTool mathTool) {
        register(searchTool);
        register(fileTool);
        register(mathTool);
    }

    private void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Map<String, Tool> all() {
        return tools;
    }
}

