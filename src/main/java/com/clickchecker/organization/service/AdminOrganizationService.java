package com.clickchecker.organization.service;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.organization.entity.Organization;
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

    private final AccountRepository accountRepository;
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
}
