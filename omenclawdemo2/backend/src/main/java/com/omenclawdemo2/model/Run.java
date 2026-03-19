package com.omenclawdemo2.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "runs")
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_message_id", nullable = false)
    private Message rootMessage;

    @Column(nullable = false, length = 32)
    private String phase;

    @Column(name = "plan_json", columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    public Long getId() { return id; }
    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }
    public Message getRootMessage() { return rootMessage; }
    public void setRootMessage(Message rootMessage) { this.rootMessage = rootMessage; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
