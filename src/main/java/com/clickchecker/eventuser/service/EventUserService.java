package com.clickchecker.eventuser.service;

import com.clickchecker.eventuser.dto.EventUserCreateRequest;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.mapper.EventUserMapper;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class EventUserService {

    private final EventUserRepository eventUserRepository;
    private final OrganizationRepository organizationRepository;
    private final EventUserMapper eventUserMapper;

    @Transactional
    public Long create(EventUserCreateRequest request) {
        Organization organization = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid organizationId."));

        if (eventUserRepository.existsByOrganizationIdAndExternalUserId(request.organizationId(), request.externalUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicated externalUserId in organization.");
        }

        EventUser mapped = eventUserMapper.toEntity(request);
        EventUser eventUser = EventUser.builder()
                .organization(organization)
                .externalUserId(mapped.getExternalUserId())
                .build();

        return eventUserRepository.save(eventUser).getId();
    }
}
