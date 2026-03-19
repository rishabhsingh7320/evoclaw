package com.openclaw4.model;

import jakarta.persistence.*;

@Entity
@Table(name = "task_dependencies")
public class TaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on_task_id", nullable = false)
    private Task dependsOn;

    public Long getId() { return id; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public Task getDependsOn() { return dependsOn; }
    public void setDependsOn(Task dependsOn) { this.dependsOn = dependsOn; }
}
