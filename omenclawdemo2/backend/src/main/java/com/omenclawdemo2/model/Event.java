package com.omenclawdemo2.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    @Column(name = "task_id")
    private Long taskId;

    @Column(nullable = false, length = 64)
    private String kind;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Run getRun() { return run; }
    public void setRun(Run run) { this.run = run; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
}
