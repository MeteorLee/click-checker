package com.clickchecker.eventuser.controller;

import com.clickchecker.eventuser.dto.EventUserCreateRequest;
import com.clickchecker.eventuser.service.EventUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/event-users")
public class EventUserController {

    private final EventUserService eventUserService;

    @PostMapping
    public ResponseEntity<CreateResponse> create(@RequestBody @Valid EventUserCreateRequest request) {
        Long id = eventUserService.create(request);
        return ResponseEntity.ok(new CreateResponse(id));
    }

    public record CreateResponse(Long id) {
    }
}
