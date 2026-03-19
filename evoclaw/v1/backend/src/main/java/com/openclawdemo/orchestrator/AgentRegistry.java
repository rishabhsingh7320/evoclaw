package com.openclawdemo.orchestrator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AgentRegistry {

    private final Map<String, Agent> agents = new HashMap<>();

    public AgentRegistry(ManagerAgent managerAgent) {
        register(managerAgent);
    }

    private void register(Agent agent) {
        agents.put(agent.getId(), agent);
    }

    public Agent get(String id) {
        return agents.get(id);
    }
}

