package com.clickchecker.organizationmember.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.clickchecker.organization.repository.OrganizationRepository;
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
public class OrganizationMemberAccessService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Transactional(readOnly = true)
    public OrganizationMember requireMemberWithAtLeastRole(
            Long accountId,
            Long organizationId,
            OrganizationRole requiredRole
    ) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_NOT_FOUND);
        }

        OrganizationMember membership = organizationMemberRepository.findByAccountIdAndOrganizationId(accountId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN));

        if (!membership.hasRoleAtLeast(requiredRole)) {
            throw new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN);
        }

        return membership;
    }
}
