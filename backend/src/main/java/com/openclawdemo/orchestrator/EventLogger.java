package com.openclawdemo.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclawdemo.api.dto.EventDto;
import com.openclawdemo.model.Event;
import com.openclawdemo.model.Run;
import com.openclawdemo.model.Step;
import com.openclawdemo.repository.EventRepository;
import com.openclawdemo.websocket.RunEventWebSocketHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventLogger {

    private final EventRepository eventRepository;
    private final RunEventWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventLogger(EventRepository eventRepository, RunEventWebSocketHandler webSocketHandler) {
        this.eventRepository = eventRepository;
        this.webSocketHandler = webSocketHandler;
    }

    public Event log(Run run, Step step, String kind, Map<String, Object> payload) {
        Event event = new Event();
        event.setRun(run);
        event.setStep(step);
        event.setKind(kind);
        event.setPayloadJson(serialize(payload));
        Event saved = eventRepository.save(event);

        EventDto dto = new EventDto();
        dto.setId(saved.getId());
        dto.setRunId(run.getId());
        dto.setKind(saved.getKind());
        dto.setPayloadJson(saved.getPayloadJson());
        dto.setCreatedAt(saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null);
        if (step != null) {
            dto.setStepId(step.getId());
            dto.setStepIndex(step.getStepIndex());
        }
        webSocketHandler.sendEvent(dto);

        return saved;
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

