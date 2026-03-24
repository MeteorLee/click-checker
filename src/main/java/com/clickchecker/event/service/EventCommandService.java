package com.clickchecker.event.service;

import com.clickchecker.event.controller.request.EventCreateRequest;
import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserCommandRepository;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EventCommandService {

    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final EventUserRepository eventUserRepository;
    private final EventUserCommandRepository eventUserCommandRepository;

    @Transactional
    public Long create(Long authOrgId, EventCreateRequest req) {
        Organization organization = organizationRepository.getReferenceById(authOrgId);

        EventUser eventUser = null;
        if (req.externalUserId() != null && !req.externalUserId().isBlank()) {
            Long eventUserId = eventUserCommandRepository.findOrCreateUserId(authOrgId, req.externalUserId());
            eventUser = eventUserRepository.getReferenceById(eventUserId);
        }

        Event event = Event.builder()
                .eventType(req.eventType())
                .path(req.path())
                .organization(organization)
                .eventUser(eventUser)
                .occurredAt(req.occurredAt())
                .payload(req.payload())
                .build();

        return eventRepository.save(event).getId();
    }
}
