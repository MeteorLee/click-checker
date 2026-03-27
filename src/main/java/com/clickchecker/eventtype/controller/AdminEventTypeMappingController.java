package com.clickchecker.eventtype.controller;

import com.clickchecker.eventtype.controller.request.EventTypeMappingActiveUpdateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingCreateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingUpdateRequest;
import com.clickchecker.eventtype.controller.response.EventTypeMappingItem;
import com.clickchecker.eventtype.controller.response.EventTypeMappingListResponse;
import com.clickchecker.eventtype.service.AdminEventTypeMappingService;
import com.clickchecker.security.principal.AdminPrincipal;
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
@RequestMapping("/api/v1/admin/organizations/{organizationId}/event-type-mappings")
@RequiredArgsConstructor
public class AdminEventTypeMappingController {

    private final AdminEventTypeMappingService adminEventTypeMappingService;

    @GetMapping
    public EventTypeMappingListResponse getAll(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId
    ) {
        return new EventTypeMappingListResponse(
                adminEventTypeMappingService.getAll(principal.accountId(), organizationId)
        );
    }

    @PostMapping
    public ResponseEntity<EventTypeMappingItem> create(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestBody @Valid EventTypeMappingCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminEventTypeMappingService.create(principal.accountId(), organizationId, request));
    }

    @PutMapping("/{eventTypeMappingId}")
    public EventTypeMappingItem update(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @PathVariable Long eventTypeMappingId,
            @RequestBody @Valid EventTypeMappingUpdateRequest request
    ) {
        return adminEventTypeMappingService.update(
                principal.accountId(),
                organizationId,
                eventTypeMappingId,
                request
        );
    }

    @PutMapping("/{eventTypeMappingId}/active")
    public EventTypeMappingItem updateActive(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @PathVariable Long eventTypeMappingId,
            @RequestBody @Valid EventTypeMappingActiveUpdateRequest request
    ) {
        return adminEventTypeMappingService.updateActive(
                principal.accountId(),
                organizationId,
                eventTypeMappingId,
                request
        );
    }

    @DeleteMapping("/{eventTypeMappingId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @PathVariable Long eventTypeMappingId
    ) {
        adminEventTypeMappingService.delete(principal.accountId(), organizationId, eventTypeMappingId);
        return ResponseEntity.noContent().build();
    }
}
