package com.clickchecker.eventtype.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.clickchecker.eventtype.controller.request.EventTypeMappingCreateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingActiveUpdateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingUpdateRequest;
import com.clickchecker.eventtype.controller.response.EventTypeMappingItem;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.web.error.ApiErrorMessages;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EventTypeMappingService {

    private final EventTypeMappingRepository eventTypeMappingRepository;
    private final OrganizationRepository organizationRepository;
    private final EventTypeMappingCacheService eventTypeMappingCacheService;

    @Transactional
    public EventTypeMapping create(Long organizationId, EventTypeMappingCreateRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_NOT_FOUND));

        EventTypeMapping eventTypeMapping = EventTypeMapping.builder()
                .organization(organization)
                .rawEventType(request.rawEventType())
                .canonicalEventType(request.canonicalEventType())
                .active(true)
                .build();

        EventTypeMapping saved = eventTypeMappingRepository.save(eventTypeMapping);
        eventTypeMappingCacheService.evictActiveMappingsAfterCommit(organizationId);
        return saved;
    }

    @Transactional
    public EventTypeMapping update(Long organizationId, Long eventTypeMappingId, EventTypeMappingUpdateRequest request) {
        EventTypeMapping eventTypeMapping = eventTypeMappingRepository.findByIdAndOrganizationId(eventTypeMappingId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.EVENT_TYPE_MAPPING_NOT_FOUND));

        eventTypeMapping.update(
                request.rawEventType(),
                request.canonicalEventType()
        );

        eventTypeMappingCacheService.evictActiveMappingsAfterCommit(organizationId);
        return eventTypeMapping;
    }

    @Transactional
    public EventTypeMapping updateActive(
            Long organizationId,
            Long eventTypeMappingId,
            EventTypeMappingActiveUpdateRequest request
    ) {
        EventTypeMapping eventTypeMapping = eventTypeMappingRepository.findByIdAndOrganizationId(eventTypeMappingId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.EVENT_TYPE_MAPPING_NOT_FOUND));

        if (Boolean.TRUE.equals(request.active())) {
            eventTypeMapping.activate();
        } else {
            eventTypeMapping.deactivate();
        }

        eventTypeMappingCacheService.evictActiveMappingsAfterCommit(organizationId);
        return eventTypeMapping;
    }

    @Transactional
    public void delete(Long organizationId, Long eventTypeMappingId) {
        EventTypeMapping eventTypeMapping = eventTypeMappingRepository.findByIdAndOrganizationId(eventTypeMappingId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.EVENT_TYPE_MAPPING_NOT_FOUND));

        eventTypeMappingRepository.delete(eventTypeMapping);
        eventTypeMappingCacheService.evictActiveMappingsAfterCommit(organizationId);
    }

    @Transactional(readOnly = true)
    public List<EventTypeMappingItem> getAll(Long organizationId) {
        return eventTypeMappingRepository.findByOrganizationIdOrderByRawEventTypeAscIdAsc(organizationId).stream()
                .map(eventTypeMapping -> new EventTypeMappingItem(
                        eventTypeMapping.getId(),
                        eventTypeMapping.getRawEventType(),
                        eventTypeMapping.getCanonicalEventType(),
                        eventTypeMapping.isActive()
                ))
                .toList();
    }
}
