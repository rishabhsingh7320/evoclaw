package com.openclaw4.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "user_id")
    private String userId;

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
