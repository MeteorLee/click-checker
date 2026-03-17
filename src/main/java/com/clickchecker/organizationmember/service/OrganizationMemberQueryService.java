package com.clickchecker.organizationmember.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberQueryRepository;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class OrganizationMemberQueryService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationMemberQueryRepository organizationMemberQueryRepository;

    @Transactional(readOnly = true)
    public List<OrganizationMemberSummary> getMembers(Long accountId, Long organizationId) {
        requireMemberWithAtLeastAdminRole(accountId, organizationId);

        return organizationMemberQueryRepository.findMembersByOrganizationId(organizationId)
                .stream()
                .map(member -> new OrganizationMemberSummary(
                        member.getId(),
                        member.getAccount().getId(),
                        member.getAccount().getLoginId(),
                        member.getAccount().getStatus().name(),
                        member.getRole().name()
                ))
                .toList();
    }

    private void requireMemberWithAtLeastAdminRole(Long accountId, Long organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResponseStatusException(NOT_FOUND, "Organization not found.");
        }

        OrganizationMember membership = organizationMemberRepository.findByAccountIdAndOrganizationId(accountId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Forbidden."));

        if (!membership.hasRoleAtLeast(OrganizationRole.ADMIN)) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden.");
        }
    }

    public record OrganizationMemberSummary(
            Long memberId,
            Long accountId,
            String loginId,
            String accountStatus,
            String role
    ) {
    }
}
