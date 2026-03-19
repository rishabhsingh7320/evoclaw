package com.openclaw3.api.dto;

import java.util.List;

public class TaskDto {

    private Long id;
    private String externalId;
    private String title;
    private String description;
    private String status;
    private String resultText;
    private int sortOrder;
    private List<String> dependsOn;
    private List<TaskStepDto> steps;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
    public List<TaskStepDto> getSteps() { return steps; }
    public void setSteps(List<TaskStepDto> steps) { this.steps = steps; }
}
