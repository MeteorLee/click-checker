package com.clickchecker.eventtype.controller;

import com.clickchecker.eventtype.controller.request.EventTypeMappingCreateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingActiveUpdateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingUpdateRequest;
import com.clickchecker.eventtype.controller.response.EventTypeMappingListResponse;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.service.EventTypeMappingService;
import com.clickchecker.web.resolver.CurrentOrganizationId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public EventTypeMappingListResponse getAll(@CurrentOrganizationId Long authOrgId) {
        return new EventTypeMappingListResponse(eventTypeMappingService.getAll(authOrgId));
    }

    @PostMapping
    public ResponseEntity<CreateResponse> create(
            @CurrentOrganizationId Long authOrgId,
            @RequestBody @Valid EventTypeMappingCreateRequest request
    ) {
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
            @CurrentOrganizationId Long authOrgId,
            @PathVariable Long eventTypeMappingId,
            @RequestBody @Valid EventTypeMappingUpdateRequest request
    ) {
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
            @CurrentOrganizationId Long authOrgId,
            @PathVariable Long eventTypeMappingId,
            @RequestBody @Valid EventTypeMappingActiveUpdateRequest request
    ) {
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
            @CurrentOrganizationId Long authOrgId,
            @PathVariable Long eventTypeMappingId
    ) {
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
