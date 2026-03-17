package com.clickchecker.eventtype.service;

import com.clickchecker.eventtype.controller.request.EventTypeMappingCreateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingActiveUpdateRequest;
import com.clickchecker.eventtype.controller.request.EventTypeMappingUpdateRequest;
import com.clickchecker.eventtype.controller.response.EventTypeMappingItem;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventTypeMappingService {

    private final EventTypeMappingRepository eventTypeMappingRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public EventTypeMapping create(Long organizationId, EventTypeMappingCreateRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found: " + organizationId));

        EventTypeMapping eventTypeMapping = EventTypeMapping.builder()
                .organization(organization)
                .rawEventType(request.rawEventType())
                .canonicalEventType(request.canonicalEventType())
                .active(true)
                .build();

        return eventTypeMappingRepository.save(eventTypeMapping);
    }

    @Transactional
    public EventTypeMapping update(Long organizationId, Long eventTypeMappingId, EventTypeMappingUpdateRequest request) {
        EventTypeMapping eventTypeMapping = eventTypeMappingRepository.findByIdAndOrganizationId(eventTypeMappingId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("EventTypeMapping not found: " + eventTypeMappingId));

        eventTypeMapping.update(
                request.rawEventType(),
                request.canonicalEventType()
        );

        return eventTypeMapping;
    }

    @Transactional
    public EventTypeMapping updateActive(
            Long organizationId,
            Long eventTypeMappingId,
            EventTypeMappingActiveUpdateRequest request
    ) {
        EventTypeMapping eventTypeMapping = eventTypeMappingRepository.findByIdAndOrganizationId(eventTypeMappingId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("EventTypeMapping not found: " + eventTypeMappingId));

        if (Boolean.TRUE.equals(request.active())) {
            eventTypeMapping.activate();
        } else {
            eventTypeMapping.deactivate();
        }

        return eventTypeMapping;
    }

    @Transactional
    public void delete(Long organizationId, Long eventTypeMappingId) {
        EventTypeMapping eventTypeMapping = eventTypeMappingRepository.findByIdAndOrganizationId(eventTypeMappingId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("EventTypeMapping not found: " + eventTypeMappingId));

        eventTypeMappingRepository.delete(eventTypeMapping);
    }

    @Transactional(readOnly = true)
    public List<EventTypeMappingItem> getAll(Long organizationId) {
        return eventTypeMappingRepository.findAll().stream()
                .filter(eventTypeMapping -> eventTypeMapping.getOrganization().getId().equals(organizationId))
                .sorted(Comparator
                        .comparing(EventTypeMapping::getRawEventType)
                        .thenComparing(EventTypeMapping::getId))
                .map(eventTypeMapping -> new EventTypeMappingItem(
                        eventTypeMapping.getId(),
                        eventTypeMapping.getRawEventType(),
                        eventTypeMapping.getCanonicalEventType(),
                        eventTypeMapping.isActive()
                ))
                .toList();
    }
}
