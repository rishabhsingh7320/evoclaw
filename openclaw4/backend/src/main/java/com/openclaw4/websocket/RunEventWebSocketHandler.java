package com.openclaw4.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw4.api.dto.EventDto;
import com.openclaw4.model.Run;
import com.openclaw4.repository.RunRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunEventWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RunRepository runRepository;
    private final Map<Long, Set<WebSocketSession>> sessionsByRun = new ConcurrentHashMap<>();

    public RunEventWebSocketHandler(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long runId = extractRunId(session);
        if (runId != null) {
            sessionsByRun.computeIfAbsent(runId, id -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long runId = extractRunId(session);
        if (runId != null) {
            Set<WebSocketSession> sessions = sessionsByRun.get(runId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) sessionsByRun.remove(runId);
            }
        }
    }

    public void sendEvent(EventDto dto) {
        if (dto == null || dto.getRunId() == null) return;
        try {
            String payload = objectMapper.writeValueAsString(dto);
            TextMessage message = new TextMessage(payload);
            sendToSubscribers(dto.getRunId(), message);
            // If this run is a child run, also send to parent's subscribers so the UI shows sub-run activity
            runRepository.findById(dto.getRunId()).ifPresent(run -> {
                Long parentId = run.getParentRunId();
                if (parentId != null) sendToSubscribers(parentId, message);
            });
        } catch (IOException ignored) {
        }
    }

    private void sendToSubscribers(Long runId, TextMessage message) {
        Set<WebSocketSession> sessions = sessionsByRun.get(runId);
        if (sessions == null || sessions.isEmpty()) return;
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) try { s.sendMessage(message); } catch (IOException ignored) {}
        }
    }

    private Long extractRunId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : null;
        if (path == null) return null;
        String[] parts = path.split("/");
        if (parts.length < 4) return null;
        try { return Long.parseLong(parts[3]); }
        catch (NumberFormatException e) { return null; }
    }
}
