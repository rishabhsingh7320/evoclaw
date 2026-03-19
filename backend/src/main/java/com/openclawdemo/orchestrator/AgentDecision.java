package com.openclawdemo.orchestrator;

import java.util.Map;

public class AgentDecision {

    public enum Type {
        RESPOND,
        TOOL_CALL
    }

    private final Type type;
    private final String responseText;
    private final String toolName;
    private final Map<String, Object> toolArgs;

    private AgentDecision(Type type, String responseText, String toolName, Map<String, Object> toolArgs) {
        this.type = type;
        this.responseText = responseText;
        this.toolName = toolName;
        this.toolArgs = toolArgs;
    }

    public static AgentDecision respond(String text) {
        return new AgentDecision(Type.RESPOND, text, null, null);
    }

    public static AgentDecision toolCall(String toolName, Map<String, Object> args) {
        return new AgentDecision(Type.TOOL_CALL, null, toolName, args);
    }

    public Type getType() {
        return type;
    }

    public String getResponseText() {
        return responseText;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getToolArgs() {
        return toolArgs;
    }
}

