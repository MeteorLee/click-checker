package com.clickchecker.eventtype.service;

import com.clickchecker.eventtype.controller.request.EventTypeMappingActiveUpdateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingCreateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingUpdateRequest;
import com.clickchecker.eventtype.controller.response.EventTypeMappingItem;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.service.OrganizationMemberAccessService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminEventTypeMappingService {

    private final OrganizationMemberAccessService organizationMemberAccessService;
    private final EventTypeMappingService eventTypeMappingService;

    @Transactional(readOnly = true)
    public List<EventTypeMappingItem> getAll(Long accountId, Long organizationId) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.VIEWER
        );
        return eventTypeMappingService.getAll(organizationId);
    }

    @Transactional
    public EventTypeMappingItem create(Long accountId, Long organizationId, EventTypeMappingCreateRequest request) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.ADMIN
        );
        EventTypeMapping mapping = eventTypeMappingService.create(organizationId, request);
        return toItem(mapping);
    }

    @Transactional
    public EventTypeMappingItem update(
            Long accountId,
            Long organizationId,
            Long eventTypeMappingId,
            EventTypeMappingUpdateRequest request
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.ADMIN
        );
        EventTypeMapping mapping = eventTypeMappingService.update(organizationId, eventTypeMappingId, request);
        return toItem(mapping);
    }

    @Transactional
    public EventTypeMappingItem updateActive(
            Long accountId,
            Long organizationId,
            Long eventTypeMappingId,
            EventTypeMappingActiveUpdateRequest request
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.ADMIN
        );
        EventTypeMapping mapping = eventTypeMappingService.updateActive(organizationId, eventTypeMappingId, request);
        return toItem(mapping);
    }

    @Transactional
    public void delete(Long accountId, Long organizationId, Long eventTypeMappingId) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.ADMIN
        );
        eventTypeMappingService.delete(organizationId, eventTypeMappingId);
    }

    private EventTypeMappingItem toItem(EventTypeMapping mapping) {
        return new EventTypeMappingItem(
                mapping.getId(),
                mapping.getRawEventType(),
                mapping.getCanonicalEventType(),
                mapping.isActive()
        );
    }
}
