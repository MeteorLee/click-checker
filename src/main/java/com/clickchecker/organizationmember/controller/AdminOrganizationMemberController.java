package com.clickchecker.organizationmember.controller;

import com.clickchecker.organizationmember.controller.response.AdminOrganizationMemberListResponse;
import com.clickchecker.organizationmember.controller.response.AdminOrganizationMemberResponse;
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
    public AdminOrganizationMemberListResponse getMembers(
            @CurrentAccountId Long accountId,
            @PathVariable Long organizationId
    ) {
        List<AdminOrganizationMemberResponse> members = organizationMemberQueryService.getMembers(accountId, organizationId)
                .stream()
                .map(member -> new AdminOrganizationMemberResponse(
                        member.memberId(),
                        member.accountId(),
                        member.loginId(),
                        member.accountStatus(),
                        member.role()
                ))
                .toList();

        return new AdminOrganizationMemberListResponse(members);
    }
}
