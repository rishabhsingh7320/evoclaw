package com.omenclawdemo2.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omenclawdemo2.api.dto.EventDto;
import com.omenclawdemo2.model.Event;
import com.omenclawdemo2.model.Run;
import com.omenclawdemo2.model.Task;
import com.omenclawdemo2.repository.EventRepository;
import com.omenclawdemo2.websocket.RunEventWebSocketHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventLogger {

    private final EventRepository eventRepository;
    private final RunEventWebSocketHandler webSocketHandler;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventLogger(EventRepository eventRepository, RunEventWebSocketHandler webSocketHandler) {
        this.eventRepository = eventRepository;
        this.webSocketHandler = webSocketHandler;
    }

    public Event log(Run run, Task task, String kind, Map<String, Object> payload) {
        Event event = new Event();
        event.setRun(run);
        event.setTaskId(task != null ? task.getId() : null);
        event.setKind(kind);
        event.setPayloadJson(serialize(payload));
        Event saved = eventRepository.save(event);

        EventDto dto = new EventDto();
        dto.setId(saved.getId());
        dto.setRunId(run.getId());
        dto.setKind(saved.getKind());
        dto.setPayloadJson(saved.getPayloadJson());
        dto.setCreatedAt(saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null);
        if (task != null) {
            dto.setTaskId(task.getId());
            dto.setTaskExternalId(task.getExternalId());
        }
        webSocketHandler.sendEvent(dto);
        return saved;
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null) return null;
        try { return mapper.writeValueAsString(payload); }
        catch (JsonProcessingException e) { return null; }
    }
}
