package com.clickchecker.event.controller;

import com.clickchecker.event.dto.EventCreateRequest;
import com.clickchecker.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/events")
public class EventCommandController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<CreateResponse> create(@RequestBody EventCreateRequest request) {
        Long id = eventService.create(request.eventType(), request.occurredAt(), request.payload());
        return ResponseEntity.ok(new CreateResponse(id));
    }

    public record CreateResponse(Long id) {}
}
