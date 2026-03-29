package com.clickchecker.eventtype.controller;

import com.clickchecker.eventtype.controller.request.EventTypeMappingCreateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingActiveUpdateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingUpdateRequest;
import com.clickchecker.eventtype.controller.response.EventTypeMappingListResponse;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.security.principal.ApiKeyPrincipal;
import com.clickchecker.eventtype.service.EventTypeMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/event-type-mappings")
@RequiredArgsConstructor
public class EventTypeMappingController {

    private final EventTypeMappingService eventTypeMappingService;

    @GetMapping
    public EventTypeMappingListResponse getAll(@AuthenticationPrincipal ApiKeyPrincipal principal) {
        Long authOrgId = principal.organizationId();
        return new EventTypeMappingListResponse(eventTypeMappingService.getAll(authOrgId));
    }

    @PostMapping
    public ResponseEntity<CreateResponse> create(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestBody @Valid EventTypeMappingCreateRequest request
    ) {
        Long authOrgId = principal.organizationId();
        EventTypeMapping eventTypeMapping = eventTypeMappingService.create(authOrgId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateResponse(
                        eventTypeMapping.getId(),
                        eventTypeMapping.getRawEventType(),
                        eventTypeMapping.getCanonicalEventType(),
                        eventTypeMapping.isActive()
                ));
    }

    @PutMapping("/{eventTypeMappingId}")
    public CreateResponse update(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @PathVariable Long eventTypeMappingId,
            @RequestBody @Valid EventTypeMappingUpdateRequest request
    ) {
        Long authOrgId = principal.organizationId();
        EventTypeMapping eventTypeMapping = eventTypeMappingService.update(authOrgId, eventTypeMappingId, request);

        return new CreateResponse(
                eventTypeMapping.getId(),
                eventTypeMapping.getRawEventType(),
                eventTypeMapping.getCanonicalEventType(),
                eventTypeMapping.isActive()
        );
    }

    @PutMapping("/{eventTypeMappingId}/active")
    public CreateResponse updateActive(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @PathVariable Long eventTypeMappingId,
            @RequestBody @Valid EventTypeMappingActiveUpdateRequest request
    ) {
        Long authOrgId = principal.organizationId();
        EventTypeMapping eventTypeMapping = eventTypeMappingService.updateActive(authOrgId, eventTypeMappingId, request);

        return new CreateResponse(
                eventTypeMapping.getId(),
                eventTypeMapping.getRawEventType(),
                eventTypeMapping.getCanonicalEventType(),
                eventTypeMapping.isActive()
        );
    }

    @DeleteMapping("/{eventTypeMappingId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @PathVariable Long eventTypeMappingId
    ) {
        Long authOrgId = principal.organizationId();
        eventTypeMappingService.delete(authOrgId, eventTypeMappingId);
        return ResponseEntity.noContent().build();
    }

    public record CreateResponse(
            Long id,
            String rawEventType,
            String canonicalEventType,
            boolean active
    ) {
    }
}
