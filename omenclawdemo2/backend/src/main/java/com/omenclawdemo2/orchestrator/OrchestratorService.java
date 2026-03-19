package com.omenclawdemo2.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omenclawdemo2.llm.LlmClient;
import com.omenclawdemo2.model.*;
import com.omenclawdemo2.repository.*;
import com.omenclawdemo2.tools.Tool;
import com.omenclawdemo2.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
    private static final int MAX_STEPS_PER_TASK = 8;

    private final RunRepository runRepository;
    private final MessageRepository messageRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskStepRepository taskStepRepository;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final EventLogger eventLogger;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrchestratorService(RunRepository runRepository,
                               MessageRepository messageRepository,
                               TaskRepository taskRepository,
                               TaskDependencyRepository taskDependencyRepository,
                               TaskStepRepository taskStepRepository,
                               LlmClient llmClient,
                               ToolRegistry toolRegistry,
                               EventLogger eventLogger) {
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.taskRepository = taskRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.taskStepRepository = taskStepRepository;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.eventLogger = eventLogger;
    }

    @Async
    @Transactional
    public void startRun(Long runId) {
        sleep(300);
        Run run = runRepository.findById(runId).orElseThrow();
        String userQuery = run.getRootMessage().getContent();

        eventLogger.log(run, null, "run_started", Map.of("runId", runId, "query", userQuery));

        try {
            planPhase(run, userQuery);
            executePhase(run, userQuery);
        } catch (Exception e) {
            log.error("Run {} failed", runId, e);
            run.setPhase("failed");
            run.setFinishedAt(Instant.now());
            runRepository.save(run);
            eventLogger.log(run, null, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "unknown"));
            eventLogger.log(run, null, "run_finished", Map.of("phase", "failed"));
        }
    }

    // ── Phase 1: Planning ──────────────────────────────────────────────

    private void planPhase(Run run, String userQuery) {
        eventLogger.log(run, null, "planning_started", Map.of());

        String systemPrompt = """
            You are a task planner. Given a user query, break it down into a directed acyclic graph (DAG) of tasks.
            Each task has an id (e.g. "t1", "t2"), a title, a description of what to do, and a list of task ids it depends on.
            Tasks that have no dependencies can run first.
            
            You MUST respond with ONLY a JSON object in this exact format (no extra text, no markdown):
            {
              "tasks": [
                { "id": "t1", "title": "...", "description": "...", "dependsOn": [] },
                { "id": "t2", "title": "...", "description": "...", "dependsOn": ["t1"] }
              ]
            }
            
            Create 3-6 tasks. Make sure the graph is a valid DAG (no cycles).
            Available tools the executor can use for each task: """ + toolRegistry.buildToolDescriptions();

        eventLogger.log(run, null, "llm_call", Map.of(
                "phase", "planning",
                "systemPrompt", systemPrompt.substring(0, Math.min(systemPrompt.length(), 500)) + "...",
                "userPrompt", userQuery
        ));

        String content = llmClient.chat(systemPrompt, userQuery);
        String json = llmClient.extractJson(content);

        eventLogger.log(run, null, "llm_response", Map.of(
                "phase", "planning",
                "rawResponse", json.substring(0, Math.min(json.length(), 1000))
        ));

        run.setPlanJson(json);
        run.setPhase("executing");
        runRepository.save(run);

        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("LLM returned invalid JSON for plan: " + json, e);
        }

        JsonNode tasksNode = root.path("tasks");
        if (!tasksNode.isArray() || tasksNode.isEmpty()) {
            throw new RuntimeException("LLM plan has no tasks: " + json);
        }

        Map<String, Task> externalIdToTask = new LinkedHashMap<>();
        int order = 0;
        for (JsonNode tn : tasksNode) {
            Task task = new Task();
            task.setRun(run);
            task.setExternalId(tn.path("id").asText("t" + order));
            task.setTitle(tn.path("title").asText("Task " + order));
            task.setDescription(tn.path("description").asText(""));
            task.setStatus("pending");
            task.setSortOrder(order++);
            task = taskRepository.save(task);
            externalIdToTask.put(task.getExternalId(), task);
        }

        for (JsonNode tn : tasksNode) {
            String extId = tn.path("id").asText();
            Task task = externalIdToTask.get(extId);
            if (task == null) continue;
            JsonNode deps = tn.path("dependsOn");
            if (deps.isArray()) {
                for (JsonNode dep : deps) {
                    Task depTask = externalIdToTask.get(dep.asText());
                    if (depTask != null) {
                        TaskDependency td = new TaskDependency();
                        td.setTask(task);
                        td.setDependsOn(depTask);
                        taskDependencyRepository.save(td);
                    }
                }
            }
        }

        List<Map<String, Object>> taskSummaries = new ArrayList<>();
        for (Task t : externalIdToTask.values()) {
            taskSummaries.add(Map.of("id", t.getExternalId(), "title", t.getTitle()));
        }
        eventLogger.log(run, null, "plan_ready", Map.of("taskCount", externalIdToTask.size(), "tasks", taskSummaries));
    }

    // ── Phase 2: Execution ─────────────────────────────────────────────

    private void executePhase(Run run, String userQuery) {
        List<Task> allTasks = taskRepository.findByRunOrderBySortOrderAsc(run);
        List<TaskDependency> allDeps = allTasks.isEmpty() ? List.of() : taskDependencyRepository.findByTaskIn(allTasks);

        List<Task> sorted = topologicalSort(allTasks, allDeps);

        Map<String, String> completedResults = new LinkedHashMap<>();

        for (Task task : sorted) {
            task.setStatus("running");
            taskRepository.save(task);
            eventLogger.log(run, task, "task_started", Map.of("taskId", task.getExternalId(), "title", task.getTitle()));

            sleep(200);

            try {
                executeTask(run, task, userQuery, completedResults);
            } catch (Exception e) {
                log.error("Task {} failed", task.getExternalId(), e);
                task.setStatus("failed");
                task.setResultText("Error: " + (e.getMessage() != null ? e.getMessage() : "unknown"));
                taskRepository.save(task);
                eventLogger.log(run, task, "task_failed", Map.of("taskId", task.getExternalId(), "error", task.getResultText()));
            }
        }

        boolean allCompleted = allTasks.stream().allMatch(t -> "completed".equals(t.getStatus()));
        run.setPhase(allCompleted ? "succeeded" : "failed");
        run.setFinishedAt(Instant.now());
        runRepository.save(run);

        StringBuilder summary = new StringBuilder();
        for (Task t : sorted) {
            summary.append("- ").append(t.getTitle()).append(": ")
                   .append(t.getResultText() != null ? t.getResultText() : "(no result)").append("\n");
        }

        Message assistantMsg = new Message();
        assistantMsg.setSession(run.getSession());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("All tasks " + (allCompleted ? "completed" : "finished (some failed)") + ".\n\n" + summary);
        messageRepository.save(assistantMsg);

        eventLogger.log(run, null, "assistant_message", Map.of("content", assistantMsg.getContent()));
        eventLogger.log(run, null, "run_finished", Map.of("phase", run.getPhase()));
    }

    private void executeTask(Run run, Task task, String userQuery, Map<String, String> completedResults) {
        String toolDescriptions = toolRegistry.buildToolDescriptions();

        StringBuilder priorContext = new StringBuilder();
        for (var entry : completedResults.entrySet()) {
            priorContext.append("Task '").append(entry.getKey()).append("' result: ").append(entry.getValue()).append("\n");
        }

        String lastToolResult = null;
        int stepIndex = 0;

        for (int i = 0; i < MAX_STEPS_PER_TASK; i++) {
            String systemPrompt = """
                You are an executor working on a specific task. You have access to real tools that return live data.
                
                Available tools:
                %s
                
                TOOL GUIDANCE:
                - Use "search" as your PRIMARY tool for getting any information (weather, news, facts, data, etc.)
                - Use "math" for ANY calculation, even simple ones — always show your work.
                - Use "file" to read or write files on the local filesystem.
                - Use "http" ONLY when you have a specific, known, working URL. Do NOT guess API URLs.
                - If a tool returns an error, try the "search" tool instead — it always works.
                
                RULES:
                1. You MUST respond with ONLY a JSON object (no extra text, no markdown fences).
                2. To call a tool: { "action": "tool_call", "tool": "<tool_name>", "arguments": { ... } }
                3. To complete the task: { "action": "complete", "result": "<your detailed answer>" }
                4. CRITICAL: Tool results are REAL and AUTHORITATIVE. Always trust and use the data they return.
                5. CRITICAL: "result" MUST contain a DETAILED answer with ALL data from tool results.
                   NEVER return an empty result. NEVER say "no result" or "no data available".
                   If tools failed, still provide your best answer based on what you know.
                6. You MUST call at least one tool before completing.
                7. After receiving tool output, incorporate that data into your final result.
                """.formatted(toolDescriptions);

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("Original user query: ").append(userQuery).append("\n\n");
            userPrompt.append("Current task: ").append(task.getTitle()).append("\n");
            userPrompt.append("Task description: ").append(task.getDescription()).append("\n\n");
            if (!priorContext.isEmpty()) {
                userPrompt.append("Results from prior tasks:\n").append(priorContext).append("\n");
            }
            if (lastToolResult != null) {
                userPrompt.append("Last tool call result:\n").append(lastToolResult).append("\n\n");
            }
            userPrompt.append("Decide your next action.");

            eventLogger.log(run, task, "llm_call", Map.of(
                    "phase", "execution",
                    "taskId", task.getExternalId(),
                    "step", i,
                    "userPrompt", userPrompt.toString()
            ));

            String content = llmClient.chat(systemPrompt, userPrompt.toString());
            String json = llmClient.extractJson(content);

            eventLogger.log(run, task, "llm_response", Map.of(
                    "phase", "execution",
                    "taskId", task.getExternalId(),
                    "step", i,
                    "decision", json.substring(0, Math.min(json.length(), 500))
            ));

            JsonNode decision;
            try {
                decision = mapper.readTree(json);
            } catch (Exception e) {
                task.setStatus("failed");
                task.setResultText("LLM returned invalid JSON: " + json);
                taskRepository.save(task);
                eventLogger.log(run, task, "task_failed", Map.of("taskId", task.getExternalId(), "error", "invalid JSON"));
                return;
            }

            String action = decision.path("action").asText("complete");

            // Force tool usage: if LLM tries to complete without any tool call, auto-search
            if (!"tool_call".equals(action) && stepIndex == 0) {
                log.info("Task {} tried to complete without tool usage — forcing search", task.getExternalId());
                String autoQuery = task.getTitle() + " " + (task.getDescription() != null ? task.getDescription() : "");
                Tool searchTool = toolRegistry.get("search");
                Map<String, Object> searchArgs = Map.of("query", autoQuery);
                Map<String, Object> searchResult = searchTool != null ? searchTool.call(searchArgs) : Map.of("output", "no search available");

                TaskStep autoStep = new TaskStep();
                autoStep.setTask(task);
                autoStep.setStepIndex(stepIndex++);
                autoStep.setKind("tool_call");
                autoStep.setToolName("search");
                autoStep.setArgumentsJson(toJson(searchArgs));
                autoStep.setResultJson(toJson(searchResult));
                taskStepRepository.save(autoStep);

                lastToolResult = toJson(searchResult);
                eventLogger.log(run, task, "task_step", Map.of(
                        "taskId", task.getExternalId(),
                        "stepIndex", autoStep.getStepIndex(),
                        "kind", "tool_call",
                        "tool", "search",
                        "args", searchArgs,
                        "result", searchResult,
                        "auto", true
                ));
                sleep(100);

                // Check if LLM provided a result text we can use anyway
                JsonNode resultNode = decision.path("result");
                if (!resultNode.isMissingNode() && !resultNode.isNull()) {
                    String inlineResult = resultNode.isTextual() ? resultNode.asText() : resultNode.toString();
                    if (!inlineResult.isBlank()) {
                        // LLM gave a good answer, combine it with tool data
                        String combined = inlineResult + "\n\n[Source data: " + lastToolResult + "]";
                        TaskStep completeStep = new TaskStep();
                        completeStep.setTask(task);
                        completeStep.setStepIndex(stepIndex);
                        completeStep.setKind("complete");
                        completeStep.setResponseText(combined);
                        taskStepRepository.save(completeStep);

                        task.setStatus("completed");
                        task.setResultText(inlineResult);
                        taskRepository.save(task);
                        completedResults.put(task.getTitle(), inlineResult);
                        eventLogger.log(run, task, "task_completed", Map.of("taskId", task.getExternalId(), "title", task.getTitle(), "result", inlineResult));
                        return;
                    }
                }
                // Otherwise continue the loop — LLM will see the search result and try again
                continue;
            }

            if ("tool_call".equals(action)) {
                String toolName = decision.path("tool").asText();
                JsonNode argsNode = decision.path("arguments");
                Map<String, Object> args = new HashMap<>();
                argsNode.fields().forEachRemaining(f -> args.put(f.getKey(), f.getValue().asText()));

                Tool tool = toolRegistry.get(toolName);
                Map<String, Object> result;
                if (tool != null) {
                    result = tool.call(args);
                } else {
                    result = Map.of("success", false, "error", "Unknown tool: " + toolName);
                }

                TaskStep step = new TaskStep();
                step.setTask(task);
                step.setStepIndex(stepIndex++);
                step.setKind("tool_call");
                step.setToolName(toolName);
                step.setArgumentsJson(toJson(args));
                step.setResultJson(toJson(result));
                taskStepRepository.save(step);

                lastToolResult = toJson(result);

                eventLogger.log(run, task, "task_step", Map.of(
                        "taskId", task.getExternalId(),
                        "stepIndex", step.getStepIndex(),
                        "kind", "tool_call",
                        "tool", toolName,
                        "args", args,
                        "result", result
                ));

                sleep(150);

            } else {
                JsonNode resultNode = decision.path("result");
                String resultText;
                if (resultNode.isMissingNode() || resultNode.isNull()) {
                    resultText = null;
                } else if (resultNode.isTextual()) {
                    resultText = resultNode.asText();
                } else {
                    resultText = resultNode.toString();
                }
                if (resultText == null || resultText.isBlank()) {
                    // LLM didn't provide a result — fallback to last tool output
                    if (lastToolResult != null && !lastToolResult.isBlank()) {
                        try {
                            JsonNode toolOut = mapper.readTree(lastToolResult);
                            resultText = toolOut.path("output").asText(lastToolResult);
                        } catch (Exception e) {
                            resultText = lastToolResult;
                        }
                    } else {
                        resultText = "(completed without detailed output)";
                    }
                }

                TaskStep step = new TaskStep();
                step.setTask(task);
                step.setStepIndex(stepIndex);
                step.setKind("complete");
                step.setResponseText(resultText);
                taskStepRepository.save(step);

                task.setStatus("completed");
                task.setResultText(resultText);
                taskRepository.save(task);
                completedResults.put(task.getTitle(), resultText);

                eventLogger.log(run, task, "task_completed", Map.of(
                        "taskId", task.getExternalId(),
                        "title", task.getTitle(),
                        "result", resultText
                ));
                return;
            }
        }

        task.setStatus("failed");
        task.setResultText("Max steps reached without completion");
        taskRepository.save(task);
        eventLogger.log(run, task, "task_failed", Map.of("taskId", task.getExternalId(), "error", "max_steps_reached"));
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private List<Task> topologicalSort(List<Task> tasks, List<TaskDependency> deps) {
        Map<Long, Task> taskById = new LinkedHashMap<>();
        for (Task t : tasks) taskById.put(t.getId(), t);

        Map<Long, Set<Long>> incomingEdges = new HashMap<>();
        Map<Long, List<Long>> outgoingEdges = new HashMap<>();
        for (Task t : tasks) {
            incomingEdges.put(t.getId(), new HashSet<>());
            outgoingEdges.put(t.getId(), new ArrayList<>());
        }
        for (TaskDependency d : deps) {
            incomingEdges.get(d.getTask().getId()).add(d.getDependsOn().getId());
            outgoingEdges.get(d.getDependsOn().getId()).add(d.getTask().getId());
        }

        Queue<Long> queue = new LinkedList<>();
        for (var e : incomingEdges.entrySet()) {
            if (e.getValue().isEmpty()) queue.add(e.getKey());
        }

        List<Task> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            sorted.add(taskById.get(id));
            for (Long next : outgoingEdges.getOrDefault(id, List.of())) {
                incomingEdges.get(next).remove(id);
                if (incomingEdges.get(next).isEmpty()) queue.add(next);
            }
        }

        if (sorted.size() < tasks.size()) {
            for (Task t : tasks) {
                if (!sorted.contains(t)) sorted.add(t);
            }
        }

        return sorted;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return mapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return null; }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
