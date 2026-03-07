package com.clickchecker.organization.service;

import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public Organization create(String name, String apiKeyKid, String apiKeyHash, String apiKeyPrefix, Instant now) {
        Organization organization = Organization.builder()
                .name(name)
                .apiKeyKid(apiKeyKid)
                .apiKeyHash(apiKeyHash)
                .apiKeyPrefix(apiKeyPrefix)
                .apiKeyCreatedAt(now)
                .build();
        return organizationRepository.save(organization);
    }
}
