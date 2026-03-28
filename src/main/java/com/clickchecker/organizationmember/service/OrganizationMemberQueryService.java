package com.clickchecker.organizationmember.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.repository.OrganizationMemberQueryRepository;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import com.clickchecker.organizationmember.service.result.OrganizationMemberResult;
import com.clickchecker.web.error.ApiErrorMessages;
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
    public List<OrganizationMemberResult> getMembers(Long accountId, Long organizationId) {
        requireMember(accountId, organizationId);

        return organizationMemberQueryRepository.findMembersByOrganizationId(organizationId)
                .stream()
                .map(member -> new OrganizationMemberResult(
                        member.getId(),
                        member.getAccount().getId(),
                        member.getAccount().getLoginId(),
                        member.getAccount().getStatus().name(),
                        member.getRole().name()
                ))
                .toList();
    }

    private void requireMember(Long accountId, Long organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_NOT_FOUND);
        }

        organizationMemberRepository.findByAccountIdAndOrganizationId(accountId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, ApiErrorMessages.FORBIDDEN));
    }
}
