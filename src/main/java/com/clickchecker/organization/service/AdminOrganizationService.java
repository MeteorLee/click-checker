package com.clickchecker.organization.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.result.AdminOrganizationCreateResult;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import java.time.Instant;
import com.clickchecker.web.error.ApiErrorMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class AdminOrganizationService {
    private static final long DEMO_ORGANIZATION_ID = 99999L;

    private final AccountRepository accountRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationService organizationService;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ApiKeyIssuer apiKeyIssuer;

    @Transactional
    public AdminOrganizationCreateResult create(Long accountId, String name) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, ApiErrorMessages.UNAUTHORIZED));

        ApiKeyIssuer.IssuedApiKey issuedApiKey = apiKeyIssuer.issue();
        Organization organization = organizationService.create(
                name.trim(),
                issuedApiKey.kid(),
                issuedApiKey.hash(),
                issuedApiKey.prefix(),
                Instant.now()
        );

        OrganizationMember ownerMembership = organizationMemberRepository.save(
                OrganizationMember.builder()
                        .account(account)
                        .organization(organization)
                        .role(OrganizationRole.OWNER)
                        .build()
        );

        return new AdminOrganizationCreateResult(
                organization.getId(),
                organization.getName(),
                ownerMembership.getId(),
                issuedApiKey.plainKey(),
                issuedApiKey.prefix()
        );
    }

    @Transactional
    public void joinDemoOrganization(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, ApiErrorMessages.UNAUTHORIZED));

        Organization organization = organizationRepository.findById(DEMO_ORGANIZATION_ID)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.DEMO_ORGANIZATION_NOT_FOUND));

        if (organizationMemberRepository.existsByAccountIdAndOrganizationId(accountId, organization.getId())) {
            return;
        }

        organizationMemberRepository.save(
                OrganizationMember.builder()
                        .account(account)
                        .organization(organization)
                        .role(OrganizationRole.VIEWER)
                        .build()
        );
    }
}
