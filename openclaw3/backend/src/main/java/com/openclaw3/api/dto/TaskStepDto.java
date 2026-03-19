package com.openclaw3.api.dto;

public class TaskStepDto {

    private Long id;
    private int stepIndex;
    private String kind;
    private String toolName;
    private String argumentsJson;
    private String resultJson;
    private String responseText;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getArgumentsJson() { return argumentsJson; }
    public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
