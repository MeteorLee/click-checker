package com.clickchecker.organizationmember.service;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import com.clickchecker.organizationmember.service.result.OrganizationMemberResult;
import com.clickchecker.web.error.ApiErrorMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class OrganizationMemberCommandService {

    public static final String SOLE_OWNER_LEAVE_CONFIRMATION =
            "혼자 남은 OWNER라 삭제 후 복구할 수 없음을 이해했습니다";

    private final AccountRepository accountRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Transactional
    public OrganizationMemberResult addMember(
            Long requesterAccountId,
            Long organizationId,
            Long targetAccountId,
            OrganizationRole role
    ) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_NOT_FOUND));

        OrganizationMember requesterMembership = organizationMemberRepository
                .findByAccountIdAndOrganizationId(requesterAccountId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN));

        if (!requesterMembership.isOwner()) {
            throw new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN);
        }

        if (organizationMemberRepository.existsByAccountIdAndOrganizationId(targetAccountId, organizationId)) {
            throw new ResponseStatusException(CONFLICT, ApiErrorMessages.ORGANIZATION_MEMBER_ALREADY_EXISTS);
        }

        Account account = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ACCOUNT_NOT_FOUND));

        OrganizationMember membership = organizationMemberRepository.save(
                OrganizationMember.builder()
                        .account(account)
                        .organization(organization)
                        .role(role)
                        .build()
        );

        return new OrganizationMemberResult(
                membership.getId(),
                account.getId(),
                account.getLoginId(),
                account.getStatus().name(),
                membership.getRole().name()
        );
    }

    @Transactional
    public OrganizationMemberResult updateRole(
            Long requesterAccountId,
            Long organizationId,
            Long memberId,
            OrganizationRole role
    ) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_NOT_FOUND));

        OrganizationMember requesterMembership = organizationMemberRepository
                .findByAccountIdAndOrganizationId(requesterAccountId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN));

        if (!requesterMembership.isOwner()) {
            throw new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN);
        }

        OrganizationMember targetMembership = organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_MEMBER_NOT_FOUND));

        if (targetMembership.isOwner() && role != OrganizationRole.OWNER
                && organizationMemberRepository.countByOrganizationIdAndRole(organizationId, OrganizationRole.OWNER) == 1) {
            throw new ResponseStatusException(CONFLICT, ApiErrorMessages.LAST_OWNER_CANNOT_BE_DEMOTED);
        }

        targetMembership.changeRole(role);

        return new OrganizationMemberResult(
                targetMembership.getId(),
                targetMembership.getAccount().getId(),
                targetMembership.getAccount().getLoginId(),
                targetMembership.getAccount().getStatus().name(),
                targetMembership.getRole().name()
        );
    }

    @Transactional
    public void removeMember(
            Long requesterAccountId,
            Long organizationId,
            Long memberId
    ) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_NOT_FOUND));

        OrganizationMember requesterMembership = organizationMemberRepository
                .findByAccountIdAndOrganizationId(requesterAccountId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN));

        if (!requesterMembership.isOwner()) {
            throw new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN);
        }

        OrganizationMember targetMembership = organizationMemberRepository.findByIdAndOrganizationId(memberId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_MEMBER_NOT_FOUND));

        if (targetMembership.isOwner()
                && organizationMemberRepository.countByOrganizationIdAndRole(organizationId, OrganizationRole.OWNER) == 1) {
            throw new ResponseStatusException(CONFLICT, ApiErrorMessages.LAST_OWNER_CANNOT_BE_REMOVED);
        }

        organizationMemberRepository.delete(targetMembership);
    }

    @Transactional
    public void leaveOrganization(Long requesterAccountId, Long organizationId, String confirmationText) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_NOT_FOUND));

        OrganizationMember requesterMembership = organizationMemberRepository
                .findByAccountIdAndOrganizationId(requesterAccountId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN));

        if (requesterMembership.isOwner()) {
            long ownerCount = organizationMemberRepository.countByOrganizationIdAndRole(organizationId, OrganizationRole.OWNER);

            if (ownerCount == 1) {
                long memberCount = organizationMemberRepository.countByOrganizationId(organizationId);

                if (memberCount > 1) {
                    throw new ResponseStatusException(CONFLICT, ApiErrorMessages.LAST_OWNER_CANNOT_BE_REMOVED);
                }

                if (!SOLE_OWNER_LEAVE_CONFIRMATION.equals(confirmationText)) {
                    throw new ResponseStatusException(CONFLICT, ApiErrorMessages.SOLE_OWNER_CONFIRMATION_REQUIRED);
                }
            }
        }

        organizationMemberRepository.delete(requesterMembership);
    }
}
