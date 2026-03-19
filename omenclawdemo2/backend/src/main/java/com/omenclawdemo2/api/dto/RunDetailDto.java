package com.omenclawdemo2.api.dto;

import java.util.List;

public class RunDetailDto {

    private Long id;
    private Long sessionId;
    private String phase;
    private String startedAt;
    private String finishedAt;
    private String userMessage;
    private String planJson;
    private List<TaskDto> tasks;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public List<TaskDto> getTasks() { return tasks; }
    public void setTasks(List<TaskDto> tasks) { this.tasks = tasks; }
}
