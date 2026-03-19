package com.openclawdemo.api;

import com.openclawdemo.model.Event;
import com.openclawdemo.model.Run;
import com.openclawdemo.repository.EventRepository;
import com.openclawdemo.repository.RunRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EventController {

    private final EventRepository eventRepository;
    private final RunRepository runRepository;

    public EventController(EventRepository eventRepository, RunRepository runRepository) {
        this.eventRepository = eventRepository;
        this.runRepository = runRepository;
    }

    @GetMapping("/runs/{runId}/events")
    public ResponseEntity<List<Event>> getEvents(@PathVariable Long runId) {
        Run run = runRepository.findById(runId).orElseThrow();
        List<Event> events = eventRepository.findByRunOrderByCreatedAtAsc(run);
        return ResponseEntity.ok(events);
    }
}

