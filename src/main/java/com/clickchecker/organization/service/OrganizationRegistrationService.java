package com.clickchecker.organization.service;

import com.clickchecker.organization.dto.OrganizationCreateRequest;
import com.clickchecker.organization.entity.Organization;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrganizationRegistrationService {

    private final OrganizationService organizationService;
    private final ApiKeyIssuer apiKeyIssuer;

    @Transactional
    public CreateResult register(OrganizationCreateRequest request) {
        ApiKeyIssuer.IssuedApiKey issued = apiKeyIssuer.issue();
        Organization organization = organizationService.create(
                request.name(),
                issued.kid(),
                issued.hash(),
                issued.prefix(),
                Instant.now()
        );

        return new CreateResult(organization.getId(), issued.plainKey(), issued.prefix());
    }

    public record CreateResult(
            Long id,
            String apiKey,
            String apiKeyPrefix
    ) {
    }
}
