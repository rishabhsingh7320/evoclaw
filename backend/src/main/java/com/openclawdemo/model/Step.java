package com.openclawdemo.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "steps")
public class Step {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private Run run;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "input_message_id")
    private Message inputMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_message_id")
    private Message outputMessage;

    @Column(name = "state_json", columnDefinition = "TEXT")
    private String stateJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Run getRun() {
        return run;
    }

    public void setRun(Run run) {
        this.run = run;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public Message getInputMessage() {
        return inputMessage;
    }

    public void setInputMessage(Message inputMessage) {
        this.inputMessage = inputMessage;
    }

    public Message getOutputMessage() {
        return outputMessage;
    }

    public void setOutputMessage(Message outputMessage) {
        this.outputMessage = outputMessage;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

