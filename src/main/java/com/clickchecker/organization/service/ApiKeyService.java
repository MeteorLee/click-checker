package com.clickchecker.organization.service;

import com.clickchecker.organization.entity.ApiKeyStatus;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Service
public class ApiKeyService {

    private final OrganizationRepository organizationRepository;
    private final ApiKeyIssuer apiKeyIssuer;

    @Transactional
    public IssuedResult issueForOrganization(Long organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid organizationId."));

        ApiKeyIssuer.IssuedApiKey issued = apiKeyIssuer.issue();
        Instant now = Instant.now();

        organization.rotateApiKey(issued.kid(), issued.hash(), issued.prefix(), now);
        organizationRepository.save(organization);

        return new IssuedResult(organization.getId(), issued.plainKey(), issued.prefix(), ApiKeyStatus.ACTIVE);
    }

    public record IssuedResult(
            Long organizationId,
            String apiKey,
            String apiKeyPrefix,
            ApiKeyStatus status
    ) {
    }
}
