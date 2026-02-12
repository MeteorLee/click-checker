package com.clickchecker.event.service;

import com.clickchecker.event.dto.EventCreateRequest;
import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class EventCommandService {

    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public Long create(EventCreateRequest req) {
        Organization organization = organizationRepository.findById(req.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid organizationId."));

        Event event = Event.builder()
                .eventType(req.eventType())
                .path(req.path())
                .organization(organization)
                .occurredAt(req.occurredAt())
                .payload(req.payload())
                .build();

        return eventRepository.save(event).getId();
    }
}
