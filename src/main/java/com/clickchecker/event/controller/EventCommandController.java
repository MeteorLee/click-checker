package com.clickchecker.event.controller;

import com.clickchecker.event.controller.request.EventCreateRequest;
import com.clickchecker.event.service.EventCommandService;
import com.clickchecker.security.principal.ApiKeyPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/events")
public class EventCommandController {

    private final EventCommandService eventCommandService;

    @PostMapping
    public ResponseEntity<CreateResponse> create(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestBody @Valid EventCreateRequest req
    ) {
        Long authOrgId = principal.organizationId();
        Long id = eventCommandService.create(authOrgId, req);
        return ResponseEntity.ok(new CreateResponse(id));
    }

    public record CreateResponse(Long id) {}
}
