package com.openclawdemo.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclawdemo.model.*;
import com.openclawdemo.repository.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class OrchestratorService {

    private final RunRepository runRepository;
    private final MessageRepository messageRepository;
    private final StepRepository stepRepository;
    private final ToolCallRepository toolCallRepository;
    private final AgentRegistry agentRegistry;
    private final ToolRegistry toolRegistry;
    private final EventLogger eventLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrchestratorService(RunRepository runRepository,
                               MessageRepository messageRepository,
                               StepRepository stepRepository,
                               ToolCallRepository toolCallRepository,
                               AgentRegistry agentRegistry,
                               ToolRegistry toolRegistry,
                               EventLogger eventLogger) {
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.stepRepository = stepRepository;
        this.toolCallRepository = toolCallRepository;
        this.agentRegistry = agentRegistry;
        this.toolRegistry = toolRegistry;
        this.eventLogger = eventLogger;
    }

    @Async
    @Transactional
    public void startRun(Long runId) {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Run run = runRepository.findById(runId).orElseThrow();
        Message userMessage = run.getRootMessage();
        Agent manager = agentRegistry.get("manager");

        eventLogger.log(run, null, "run_started", Map.of("runId", runId));

        int maxSteps = 3;
        for (int i = 0; i < maxSteps; i++) {
            AgentDecision decision = manager.decide(userMessage, i);

            Step step = new Step();
            step.setRun(run);
            step.setStepIndex(i);
            step.setAgentId(manager.getId());
            step.setInputMessage(userMessage);
            step.setStateJson(stateToJson(decision));
            step = stepRepository.save(step);

            eventLogger.log(run, step, "step_started", Map.of("stepIndex", i, "agentId", manager.getId()));

            if (decision.getType() == AgentDecision.Type.RESPOND) {
                Message assistantMessage = new Message();
                assistantMessage.setSession(run.getSession());
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(decision.getResponseText());
                assistantMessage = messageRepository.save(assistantMessage);

                step.setOutputMessage(assistantMessage);
                stepRepository.save(step);

                eventLogger.log(run, step, "assistant_message", Map.of("content", assistantMessage.getContent()));
                run.setStatus("succeeded");
                run.setFinishedAt(Instant.now());
                runRepository.save(run);
                eventLogger.log(run, step, "run_finished", Map.of("status", "succeeded"));
                return;
            } else if (decision.getType() == AgentDecision.Type.TOOL_CALL) {
                Tool tool = toolRegistry.get(decision.getToolName());
                ToolCall toolCall = new ToolCall();
                toolCall.setStep(step);
                toolCall.setToolName(decision.getToolName());
                toolCall.setArgumentsJson(stateToJson(decision.getToolArgs()));
                toolCall.setStatus("running");
                toolCall = toolCallRepository.save(toolCall);

                eventLogger.log(run, step, "tool_started", Map.of("tool", tool.getName()));

                Map<String, Object> result = tool.call(decision.getToolArgs());
                toolCall.setResultJson(stateToJson(result));
                toolCall.setStatus("succeeded");
                toolCall.setFinishedAt(Instant.now());
                toolCallRepository.save(toolCall);

                eventLogger.log(run, step, "tool_finished", Map.of("tool", tool.getName(), "result", result));

                String answer = formatToolAnswer(decision.getToolName(), result);
                Message assistantMessage = new Message();
                assistantMessage.setSession(run.getSession());
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(answer);
                assistantMessage = messageRepository.save(assistantMessage);

                step.setOutputMessage(assistantMessage);
                stepRepository.save(step);

                eventLogger.log(run, step, "assistant_message", Map.of("content", assistantMessage.getContent()));
                run.setStatus("succeeded");
                run.setFinishedAt(Instant.now());
                runRepository.save(run);
                eventLogger.log(run, step, "run_finished", Map.of("status", "succeeded"));
                return;
            }

            eventLogger.log(run, step, "step_finished", Map.of("stepIndex", i));
        }

        run.setStatus("succeeded");
        run.setFinishedAt(Instant.now());
        runRepository.save(run);
        eventLogger.log(run, null, "run_finished", Map.of("status", "succeeded", "reason", "max_steps_reached"));
    }

    private String formatToolAnswer(String toolName, Map<String, Object> result) {
        if (result == null) return "No result.";
        if ("search".equals(toolName) && result.containsKey("summary")) {
            return "Search result: " + result.get("summary");
        }
        if ("math".equals(toolName) && result.containsKey("result")) {
            return "Result: " + result.get("result");
        }
        if ("file".equals(toolName)) {
            if (result.containsKey("content")) return "File content: " + result.get("content");
            if (result.containsKey("status")) return "File written successfully.";
        }
        return "Result: " + result;
    }

    private String stateToJson(Object state) {
        if (state == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

