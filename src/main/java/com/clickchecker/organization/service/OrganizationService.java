package com.clickchecker.organization.service;

import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public Organization create(String name) {
        Organization organization = Organization.builder()
                .name(name)
                .build();
        return organizationRepository.save(organization);
    }
}
