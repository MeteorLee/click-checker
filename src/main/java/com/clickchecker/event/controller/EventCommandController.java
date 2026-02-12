package com.clickchecker.event.controller;

import com.clickchecker.event.dto.EventCreateRequest;
import com.clickchecker.event.service.EventService;
import jakarta.validation.Valid;
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
    public ResponseEntity<CreateResponse> create(@RequestBody @Valid EventCreateRequest req) {
        Long id = eventService.create(req);
        return ResponseEntity.ok(new CreateResponse(id));
    }

    public record CreateResponse(Long id) {}
}
