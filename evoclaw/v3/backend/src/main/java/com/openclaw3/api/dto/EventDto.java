package com.openclaw3.api.dto;

public class EventDto {

    private Long id;
    private Long runId;
    private Long taskId;
    private String taskExternalId;
    private String kind;
    private String payloadJson;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskExternalId() { return taskExternalId; }
    public void setTaskExternalId(String taskExternalId) { this.taskExternalId = taskExternalId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
