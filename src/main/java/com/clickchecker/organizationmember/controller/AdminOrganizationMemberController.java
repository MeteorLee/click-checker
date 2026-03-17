package com.clickchecker.organizationmember.controller;

import com.clickchecker.organizationmember.service.OrganizationMemberQueryService;
import com.clickchecker.web.resolver.CurrentAccountId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/organizations/{organizationId}/members")
public class AdminOrganizationMemberController {

    private final OrganizationMemberQueryService organizationMemberQueryService;

    @GetMapping
    public OrganizationMemberListResponse getMembers(
            @CurrentAccountId Long accountId,
            @PathVariable Long organizationId
    ) {
        List<OrganizationMemberResponse> members = organizationMemberQueryService.getMembers(accountId, organizationId)
                .stream()
                .map(member -> new OrganizationMemberResponse(
                        member.memberId(),
                        member.accountId(),
                        member.loginId(),
                        member.accountStatus(),
                        member.role()
                ))
                .toList();

        return new OrganizationMemberListResponse(members);
    }

    public record OrganizationMemberListResponse(
            List<OrganizationMemberResponse> members
    ) {
    }

    public record OrganizationMemberResponse(
            Long memberId,
            Long accountId,
            String loginId,
            String accountStatus,
            String role
    ) {
    }
}
