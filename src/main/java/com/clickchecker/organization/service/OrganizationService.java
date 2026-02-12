package com.clickchecker.organization.service;

import com.clickchecker.mapper.OrganizationMapper;
import com.clickchecker.organization.dto.OrganizationCreateRequest;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;

    @Transactional
    public Long create(OrganizationCreateRequest request) {
        Organization organization = organizationMapper.toEntity(request);
        return organizationRepository.save(organization).getId();
    }
}
