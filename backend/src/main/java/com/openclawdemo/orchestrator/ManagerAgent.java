package com.openclawdemo.orchestrator;

import com.openclawdemo.model.Message;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class ManagerAgent implements Agent {

    @Override
    public String getId() {
        return "manager";
    }

    @Override
    public AgentDecision decide(Message userMessage, int stepIndex) {
        String content = userMessage.getContent().toLowerCase(Locale.ROOT);
        if (content.contains("search")) {
            Map<String, Object> args = new HashMap<>();
            args.put("query", content);
            return AgentDecision.toolCall("search", args);
        }
        if (content.contains("calculate") || content.matches(".*\\d+.*")) {
            Map<String, Object> args = new HashMap<>();
            args.put("a", 2);
            args.put("b", 3);
            args.put("op", "mul");
            return AgentDecision.toolCall("math", args);
        }
        return AgentDecision.respond("ManagerAgent: answering directly: " + userMessage.getContent());
    }
}

