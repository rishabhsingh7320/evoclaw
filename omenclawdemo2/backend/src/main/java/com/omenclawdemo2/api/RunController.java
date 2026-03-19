package com.omenclawdemo2.api;

import com.omenclawdemo2.api.dto.RunDetailDto;
import com.omenclawdemo2.api.dto.TaskDto;
import com.omenclawdemo2.api.dto.TaskStepDto;
import com.omenclawdemo2.model.*;
import com.omenclawdemo2.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class RunController {

    private final RunRepository runRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskStepRepository taskStepRepository;
    private final EventRepository eventRepository;
    private final MessageRepository messageRepository;

    public RunController(RunRepository runRepository, TaskRepository taskRepository,
                         TaskDependencyRepository taskDependencyRepository,
                         TaskStepRepository taskStepRepository,
                         EventRepository eventRepository,
                         MessageRepository messageRepository) {
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.taskStepRepository = taskStepRepository;
        this.eventRepository = eventRepository;
        this.messageRepository = messageRepository;
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunDetailDto> getRun(@PathVariable Long runId) {
        Run run = runRepository.findById(runId).orElseThrow();
        List<Task> tasks = taskRepository.findByRunOrderBySortOrderAsc(run);
        List<TaskDependency> allDeps = tasks.isEmpty() ? List.of() : taskDependencyRepository.findByTaskIn(tasks);

        Map<Long, String> idToExternal = tasks.stream()
                .collect(Collectors.toMap(Task::getId, Task::getExternalId));

        Map<Long, List<String>> depMap = allDeps.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getTask().getId(),
                        Collectors.mapping(d -> idToExternal.getOrDefault(d.getDependsOn().getId(), "?"),
                                Collectors.toList())));

        List<TaskDto> taskDtos = new ArrayList<>();
        for (Task t : tasks) {
            TaskDto dto = new TaskDto();
            dto.setId(t.getId());
            dto.setExternalId(t.getExternalId());
            dto.setTitle(t.getTitle());
            dto.setDescription(t.getDescription());
            dto.setStatus(t.getStatus());
            dto.setResultText(t.getResultText());
            dto.setSortOrder(t.getSortOrder());
            dto.setDependsOn(depMap.getOrDefault(t.getId(), List.of()));

            List<TaskStep> steps = taskStepRepository.findByTaskOrderByStepIndexAsc(t);
            List<TaskStepDto> stepDtos = new ArrayList<>();
            for (TaskStep s : steps) {
                TaskStepDto sd = new TaskStepDto();
                sd.setId(s.getId());
                sd.setStepIndex(s.getStepIndex());
                sd.setKind(s.getKind());
                sd.setToolName(s.getToolName());
                sd.setArgumentsJson(s.getArgumentsJson());
                sd.setResultJson(s.getResultJson());
                sd.setResponseText(s.getResponseText());
                sd.setCreatedAt(s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
                stepDtos.add(sd);
            }
            dto.setSteps(stepDtos);
            taskDtos.add(dto);
        }

        RunDetailDto detail = new RunDetailDto();
        detail.setId(run.getId());
        detail.setSessionId(run.getSession().getId());
        detail.setPhase(run.getPhase());
        detail.setStartedAt(run.getStartedAt() != null ? run.getStartedAt().toString() : null);
        detail.setFinishedAt(run.getFinishedAt() != null ? run.getFinishedAt().toString() : null);
        detail.setUserMessage(run.getRootMessage().getContent());
        detail.setPlanJson(run.getPlanJson());
        detail.setTasks(taskDtos);
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/runs/{runId}/db")
    public ResponseEntity<Map<String, Object>> getDbSnapshot(@PathVariable Long runId) {
        Run run = runRepository.findById(runId).orElseThrow();

        Map<String, Object> snapshot = new LinkedHashMap<>();

        snapshot.put("session", Map.of(
                "id", run.getSession().getId(),
                "createdAt", run.getSession().getCreatedAt().toString()
        ));

        snapshot.put("run", Map.of(
                "id", run.getId(),
                "sessionId", run.getSession().getId(),
                "rootMessageId", run.getRootMessage().getId(),
                "phase", run.getPhase(),
                "planJson", run.getPlanJson() != null ? run.getPlanJson() : "",
                "startedAt", run.getStartedAt() != null ? run.getStartedAt().toString() : "",
                "finishedAt", run.getFinishedAt() != null ? run.getFinishedAt().toString() : ""
        ));

        List<Message> messages = messageRepository.findBySessionOrderByCreatedAtAsc(run.getSession());
        List<Map<String, Object>> msgList = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.getId());
            mm.put("role", m.getRole());
            mm.put("content", m.getContent());
            mm.put("createdAt", m.getCreatedAt().toString());
            msgList.add(mm);
        }
        snapshot.put("messages", msgList);

        List<Task> tasks = taskRepository.findByRunOrderBySortOrderAsc(run);
        List<Map<String, Object>> taskList = new ArrayList<>();
        for (Task t : tasks) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("id", t.getId());
            tm.put("externalId", t.getExternalId());
            tm.put("title", t.getTitle());
            tm.put("description", t.getDescription());
            tm.put("status", t.getStatus());
            tm.put("resultText", t.getResultText() != null ? t.getResultText() : "");
            tm.put("sortOrder", t.getSortOrder());
            taskList.add(tm);
        }
        snapshot.put("tasks", taskList);

        List<TaskDependency> deps = tasks.isEmpty() ? List.of() : taskDependencyRepository.findByTaskIn(tasks);
        List<Map<String, Object>> depList = new ArrayList<>();
        for (TaskDependency d : deps) {
            depList.add(Map.of(
                    "id", d.getId(),
                    "taskId", d.getTask().getId(),
                    "taskExternalId", d.getTask().getExternalId(),
                    "dependsOnId", d.getDependsOn().getId(),
                    "dependsOnExternalId", d.getDependsOn().getExternalId()
            ));
        }
        snapshot.put("taskDependencies", depList);

        List<Map<String, Object>> stepList = new ArrayList<>();
        for (Task t : tasks) {
            List<TaskStep> steps = taskStepRepository.findByTaskOrderByStepIndexAsc(t);
            for (TaskStep s : steps) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("id", s.getId());
                sm.put("taskId", t.getId());
                sm.put("taskExternalId", t.getExternalId());
                sm.put("stepIndex", s.getStepIndex());
                sm.put("kind", s.getKind());
                sm.put("toolName", s.getToolName() != null ? s.getToolName() : "");
                sm.put("argumentsJson", s.getArgumentsJson() != null ? s.getArgumentsJson() : "");
                sm.put("resultJson", s.getResultJson() != null ? s.getResultJson() : "");
                sm.put("responseText", s.getResponseText() != null ? s.getResponseText() : "");
                sm.put("createdAt", s.getCreatedAt().toString());
                stepList.add(sm);
            }
        }
        snapshot.put("taskSteps", stepList);

        List<Event> events = eventRepository.findByRunOrderByCreatedAtAsc(run);
        List<Map<String, Object>> evList = new ArrayList<>();
        for (Event e : events) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("id", e.getId());
            em.put("kind", e.getKind());
            em.put("taskId", e.getTaskId());
            em.put("payloadJson", e.getPayloadJson() != null ? e.getPayloadJson() : "");
            em.put("createdAt", e.getCreatedAt().toString());
            evList.add(em);
        }
        snapshot.put("events", evList);

        return ResponseEntity.ok(snapshot);
    }
}
