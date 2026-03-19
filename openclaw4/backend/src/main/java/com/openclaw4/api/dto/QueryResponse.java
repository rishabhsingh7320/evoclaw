package com.openclaw4.api.dto;

public class QueryResponse {

    private Long sessionId;
    private Long runId;

    public QueryResponse(Long sessionId, Long runId) {
        this.sessionId = sessionId;
        this.runId = runId;
    }

    public Long getSessionId() { return sessionId; }
    public Long getRunId() { return runId; }
}
