package com.clickchecker.organization.service;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.result.AdminOrganizationCreateResult;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import com.clickchecker.web.error.ApiErrorMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class AdminOrganizationService {

    private final AccountRepository accountRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Transactional
    public AdminOrganizationCreateResult create(Long accountId, String name) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, ApiErrorMessages.UNAUTHORIZED));

        Organization organization = organizationRepository.save(
                Organization.builder()
                        .name(name.trim())
                        .build()
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
                ownerMembership.getId()
        );
    }
}
