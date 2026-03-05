package com.clickchecker.organization.service;

import com.clickchecker.organization.dto.OrganizationCreateRequest;
import com.clickchecker.organization.entity.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrganizationRegistrationService {

    private final OrganizationService organizationService;
    private final ApiKeyService apiKeyService;

    @Transactional
    public CreateResult register(OrganizationCreateRequest request) {
        Organization organization = organizationService.create(request.name());
        ApiKeyService.IssuedResult issued = apiKeyService.issueForOrganization(organization.getId());
        return new CreateResult(issued.organizationId(), issued.apiKey(), issued.apiKeyPrefix());
    }

    public record CreateResult(
            Long id,
            String apiKey,
            String apiKeyPrefix
    ) {
    }
}
