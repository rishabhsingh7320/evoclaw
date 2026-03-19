package com.openclaw3.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw3.llm.LlmClient;
import com.openclaw3.model.*;
import com.openclaw3.repository.*;
import com.openclaw3.tools.MemoryTool;
import com.openclaw3.tools.Tool;
import com.openclaw3.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    @Value("${execution.max-steps-per-task:8}")
    private int maxStepsPerTask;

    @Value("${execution.parallel:false}")
    private boolean parallelExecution;

    @Value("${execution.compaction-threshold:4000}")
    private int compactionThreshold;

    @Value("${execution.loop-detection-window:20}")
    private int loopDetectionWindow;

    @Value("${execution.loop-detection-threshold:3}")
    private int loopDetectionThreshold;

    private final RunRepository runRepository;
    private final MessageRepository messageRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskStepRepository taskStepRepository;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final EventLogger eventLogger;
    private final List<OrchestratorHook> hooks;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrchestratorService(RunRepository runRepository,
                               MessageRepository messageRepository,
                               TaskRepository taskRepository,
                               TaskDependencyRepository taskDependencyRepository,
                               TaskStepRepository taskStepRepository,
                               LlmClient llmClient,
                               ToolRegistry toolRegistry,
                               EventLogger eventLogger,
                               List<OrchestratorHook> hooks) {
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.taskRepository = taskRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.taskStepRepository = taskStepRepository;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.eventLogger = eventLogger;
        this.hooks = hooks;
    }

    @Async
    @Transactional
    public void startRun(Long runId) {
        sleep(300);
        Run run = runRepository.findById(runId).orElseThrow();
        String userQuery = run.getRootMessage().getContent();

        setMemoryToolSession(run.getSession().getId());

        eventLogger.log(run, null, "run_started", Map.of(
                "runId", runId,
                "query", userQuery,
                "toolProfile", toolRegistry.getProfile(),
                "activeTools", new ArrayList<>(toolRegistry.getActiveToolNames()),
                "parentRunId", run.getParentRunId() != null ? run.getParentRunId() : 0
        ));

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
            invokeHooksOnRunEnd(run, "failed");
        }
    }

    public String runSubRun(Run parentRun, String subQuery) {
        Session session = parentRun.getSession();

        Message msg = new Message();
        msg.setSession(session);
        msg.setRole("user");
        msg.setContent(subQuery);
        msg = messageRepository.save(msg);

        Run childRun = new Run();
        childRun.setSession(session);
        childRun.setRootMessage(msg);
        childRun.setPhase("planning");
        childRun.setParentRunId(parentRun.getId());
        childRun = runRepository.save(childRun);

        eventLogger.log(parentRun, null, "sub_run_started", Map.of(
                "childRunId", childRun.getId(),
                "subQuery", subQuery
        ));

        setMemoryToolSession(session.getId());

        try {
            planPhase(childRun, subQuery);
            executePhase(childRun, subQuery);
        } catch (Exception e) {
            log.error("Sub-run {} failed", childRun.getId(), e);
            childRun.setPhase("failed");
            childRun.setFinishedAt(Instant.now());
            runRepository.save(childRun);
        }

        List<Task> childTasks = taskRepository.findByRunOrderBySortOrderAsc(childRun);
        StringBuilder result = new StringBuilder();
        for (Task t : childTasks) {
            result.append("- ").append(t.getTitle()).append(": ")
                  .append(t.getResultText() != null ? t.getResultText() : "(no result)").append("\n");
        }

        eventLogger.log(parentRun, null, "sub_run_finished", Map.of(
                "childRunId", childRun.getId(),
                "phase", childRun.getPhase(),
                "result", result.toString()
        ));

        return result.toString();
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
            
            Create 2-6 tasks. Make sure the graph is a valid DAG (no cycles).
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

        Map<String, String> completedResults = new ConcurrentHashMap<>();

        if (parallelExecution) {
            executeParallel(run, allTasks, allDeps, userQuery, completedResults);
        } else {
            executeSequential(run, allTasks, allDeps, userQuery, completedResults);
        }

        allTasks = taskRepository.findByRunOrderBySortOrderAsc(run);
        boolean allCompleted = allTasks.stream().allMatch(t -> "completed".equals(t.getStatus()));
        run.setPhase(allCompleted ? "succeeded" : "failed");
        run.setFinishedAt(Instant.now());
        runRepository.save(run);

        List<Task> sorted = topologicalSort(allTasks, allDeps);
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
        invokeHooksOnRunEnd(run, run.getPhase());
    }

    private void executeSequential(Run run, List<Task> allTasks, List<TaskDependency> allDeps,
                                    String userQuery, Map<String, String> completedResults) {
        List<Task> sorted = topologicalSort(allTasks, allDeps);
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
    }

    private void executeParallel(Run run, List<Task> allTasks, List<TaskDependency> allDeps,
                                  String userQuery, Map<String, String> completedResults) {
        List<List<Task>> levels = groupByDepth(allTasks, allDeps);

        for (List<Task> level : levels) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Task task : level) {
                task.setStatus("running");
                taskRepository.save(task);
                eventLogger.log(run, task, "task_started", Map.of("taskId", task.getExternalId(), "title", task.getTitle()));

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        setMemoryToolSession(run.getSession().getId());
                        executeTask(run, task, userQuery, completedResults);
                    } catch (Exception e) {
                        log.error("Task {} failed (parallel)", task.getExternalId(), e);
                        task.setStatus("failed");
                        task.setResultText("Error: " + (e.getMessage() != null ? e.getMessage() : "unknown"));
                        taskRepository.save(task);
                        eventLogger.log(run, task, "task_failed", Map.of("taskId", task.getExternalId(), "error", task.getResultText()));
                    }
                });
                futures.add(future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    private void executeTask(Run run, Task task, String userQuery, Map<String, String> completedResults) {
        String toolDescriptions = toolRegistry.buildToolDescriptions();
        LoopDetector loopDetector = new LoopDetector(loopDetectionWindow, loopDetectionThreshold);

        String priorContext = buildPriorContext(run, completedResults);

        String lastToolResult = null;
        int stepIndex = 0;

        for (int i = 0; i < maxStepsPerTask; i++) {
            boolean isSubRun = run.getParentRunId() != null;
            String subRunWarning = isSubRun
                    ? "\n\nIMPORTANT: You are inside a sub-run. Do NOT use the \"delegate\" action — it is disabled here. Use tools directly (search, file, math, exec, memory, http).\n\n"
                    : "";

            String systemPrompt = """
                You are an executor working on a specific task. You have access to real tools that return live data.
                %s
                Available tools:
                %s
                
                TOOL GUIDANCE:
                - Use "search" as your PRIMARY tool for getting any information (weather, news, facts, data, etc.)
                - Use "math" for ANY calculation, even simple ones — always show your work.
                - Use "file" to read or write files on the local filesystem.
                - Use "http" ONLY when you have a specific, known, working URL. Do NOT guess API URLs.
                - Use "exec" to run shell commands in a sandboxed directory (ls, echo, python, etc.)
                - Use "memory" to store/retrieve persistent facts across runs (op: get/put/list).
                - If a tool returns an error, try the "search" tool instead — it always works.
                
                RULES:
                1. You MUST respond with ONLY a JSON object (no extra text, no markdown fences).
                2. To call a tool: { "action": "tool_call", "tool": "<tool_name>", "arguments": { ... } }
                3. To complete the task: { "action": "complete", "result": "<your detailed answer>" }
                4. To delegate a subtask to a child run: { "action": "delegate", "subQuery": "<description of subtask>" }
                5. CRITICAL: Tool results are REAL and AUTHORITATIVE. Always trust and use the data they return.
                6. CRITICAL: "result" MUST contain a DETAILED answer with ALL data from tool results.
                   NEVER return an empty result. NEVER say "no result" or "no data available".
                   If tools failed, still provide your best answer based on what you know.
                7. You MUST call at least one tool before completing.
                8. After receiving tool output, incorporate that data into your final result.
                """.formatted(subRunWarning, toolDescriptions);

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

            // Force tool usage if LLM tries to complete at step 0
            if (!"tool_call".equals(action) && !"delegate".equals(action) && stepIndex == 0) {
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

                JsonNode resultNode = decision.path("result");
                if (!resultNode.isMissingNode() && !resultNode.isNull()) {
                    String inlineResult = resultNode.isTextual() ? resultNode.asText() : resultNode.toString();
                    if (!inlineResult.isBlank()) {
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
                continue;
            }

            if ("delegate".equals(action)) {
                String subQuery = decision.path("subQuery").asText("");
                if (!subQuery.isBlank() && run.getParentRunId() == null) {
                    String subResult = runSubRun(run, subQuery);

                    TaskStep step = new TaskStep();
                    step.setTask(task);
                    step.setStepIndex(stepIndex++);
                    step.setKind("delegate");
                    step.setToolName("delegate");
                    step.setArgumentsJson(toJson(Map.of("subQuery", subQuery)));
                    step.setResultJson(subResult);
                    taskStepRepository.save(step);

                    lastToolResult = subResult;
                    eventLogger.log(run, task, "task_step", Map.of(
                            "taskId", task.getExternalId(),
                            "stepIndex", step.getStepIndex(),
                            "kind", "delegate",
                            "subQuery", subQuery,
                            "result", subResult
                    ));
                } else if (run.getParentRunId() != null) {
                    lastToolResult = "{\"error\": \"Delegate is disabled in sub-runs. Use tools directly (search, file, math, exec, memory). Do NOT use delegate again.\"}";
                    eventLogger.log(run, task, "task_step", Map.of(
                            "taskId", task.getExternalId(),
                            "kind", "delegate_blocked",
                            "reason", "sub-runs cannot nest",
                            "hint", "Use search or other tools directly"
                    ));
                }
                continue;
            }

            if ("tool_call".equals(action)) {
                String toolName = decision.path("tool").asText();
                JsonNode argsNode = decision.path("arguments");
                Map<String, Object> args = new HashMap<>();
                argsNode.fields().forEachRemaining(f -> args.put(f.getKey(), f.getValue().asText()));

                // Loop detection
                boolean loopDetected = loopDetector.recordAndCheck(toolName, args);
                if (loopDetected) {
                    eventLogger.log(run, task, "loop_detection_warning", Map.of(
                            "taskId", task.getExternalId(),
                            "tool", toolName,
                            "message", "Repeated tool call detected — skipping and warning LLM"
                    ));
                    lastToolResult = "{\"warning\": \"Loop detected: you have called " + toolName + " with the same arguments " + loopDetectionThreshold + " times. Try a different approach or complete the task.\"}";
                    sleep(100);
                    continue;
                }

                // Hooks: before tool call
                for (OrchestratorHook hook : hooks) {
                    hook.beforeToolCall(run, task, toolName, args);
                }
                eventLogger.log(run, task, "hook_before_tool", Map.of(
                        "taskId", task.getExternalId(),
                        "tool", toolName
                ));

                Tool tool = toolRegistry.get(toolName);
                Map<String, Object> result;
                if (tool != null) {
                    result = tool.call(args);
                } else {
                    result = Map.of("success", false, "error", "Unknown tool: " + toolName);
                }

                // Hooks: after tool call
                for (OrchestratorHook hook : hooks) {
                    hook.afterToolCall(run, task, toolName, args, result);
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

    // ── Context Compaction ──────────────────────────────────────────────

    private String buildPriorContext(Run run, Map<String, String> completedResults) {
        if (completedResults.isEmpty()) return "";

        StringBuilder full = new StringBuilder();
        for (var entry : completedResults.entrySet()) {
            full.append("Task '").append(entry.getKey()).append("' result: ").append(entry.getValue()).append("\n");
        }

        if (full.length() <= compactionThreshold) {
            return full.toString();
        }

        eventLogger.log(run, null, "compaction_applied", Map.of(
                "originalLength", full.length(),
                "threshold", compactionThreshold,
                "strategy", "summarize"
        ));

        try {
            String summaryPrompt = "Summarize the following task results in 3-5 concise sentences, preserving key data and facts:\n\n" + full;
            String summary = llmClient.chat(
                    "You are a concise summarizer. Output only the summary, no extra text.",
                    summaryPrompt
            );
            return "(Compacted context — original " + completedResults.size() + " tasks summarized)\n" + summary + "\n";
        } catch (Exception e) {
            log.warn("Compaction LLM call failed, truncating instead: {}", e.getMessage());
            String truncated = full.substring(0, compactionThreshold);
            return "(Earlier results truncated)\n" + truncated + "\n...\n";
        }
    }

    // ── Parallel Helpers ────────────────────────────────────────────────

    private List<List<Task>> groupByDepth(List<Task> tasks, List<TaskDependency> deps) {
        Map<Long, Task> taskById = new LinkedHashMap<>();
        for (Task t : tasks) taskById.put(t.getId(), t);

        Map<Long, Set<Long>> incoming = new HashMap<>();
        Map<Long, List<Long>> outgoing = new HashMap<>();
        for (Task t : tasks) {
            incoming.put(t.getId(), new HashSet<>());
            outgoing.put(t.getId(), new ArrayList<>());
        }
        for (TaskDependency d : deps) {
            incoming.get(d.getTask().getId()).add(d.getDependsOn().getId());
            outgoing.get(d.getDependsOn().getId()).add(d.getTask().getId());
        }

        Map<Long, Integer> depth = new HashMap<>();
        Queue<Long> queue = new LinkedList<>();
        for (var e : incoming.entrySet()) {
            if (e.getValue().isEmpty()) {
                queue.add(e.getKey());
                depth.put(e.getKey(), 0);
            }
        }
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            for (Long next : outgoing.getOrDefault(id, List.of())) {
                incoming.get(next).remove(id);
                depth.put(next, Math.max(depth.getOrDefault(next, 0), depth.get(id) + 1));
                if (incoming.get(next).isEmpty()) queue.add(next);
            }
        }
        for (Task t : tasks) depth.putIfAbsent(t.getId(), 0);

        TreeMap<Integer, List<Task>> byDepth = new TreeMap<>();
        for (Task t : tasks) {
            byDepth.computeIfAbsent(depth.get(t.getId()), k -> new ArrayList<>()).add(t);
        }
        return new ArrayList<>(byDepth.values());
    }

    // ── Topological Sort ────────────────────────────────────────────────

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

    // ── Helpers ────────────────────────────────────────────────────────

    private void setMemoryToolSession(Long sessionId) {
        Tool memTool = toolRegistry.get("memory");
        if (memTool instanceof MemoryTool mt) {
            mt.setCurrentSessionId(sessionId);
        }
    }

    private void invokeHooksOnRunEnd(Run run, String phase) {
        for (OrchestratorHook hook : hooks) {
            try {
                hook.onRunEnd(run, phase);
            } catch (Exception e) {
                log.warn("Hook onRunEnd failed: {}", e.getMessage());
            }
        }
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
