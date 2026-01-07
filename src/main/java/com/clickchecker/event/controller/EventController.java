package com.clickchecker.event.controller;

import com.clickchecker.event.dto.EventCreateRequest;
import com.clickchecker.event.dto.EventCreateResponse;
import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository eventRepository;

    public EventController(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @PostMapping
    public ResponseEntity<EventCreateResponse> create(@Valid @RequestBody EventCreateRequest request) {
        Event event = new Event(
                request.getServiceId(),
                request.getTargetType(),
                request.getTargetId(),
                request.getActionType(),
                request.getOccurredAt(),
                Instant.now()
        );

        Event saved = eventRepository.save(event);
        return ResponseEntity.ok(new EventCreateResponse(saved.getId()));
    }

    @GetMapping
    public ResponseEntity<List<Event>> getAll() {
        return ResponseEntity.ok(eventRepository.findAll());
    }
}
