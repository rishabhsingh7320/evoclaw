package com.openclawdemo.orchestrator;

import com.openclawdemo.model.Message;

public interface Agent {

    String getId();

    AgentDecision decide(Message userMessage, int stepIndex);
}

