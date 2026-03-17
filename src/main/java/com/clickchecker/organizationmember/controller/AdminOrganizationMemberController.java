package com.clickchecker.organizationmember.controller;

import com.clickchecker.organizationmember.controller.response.AdminOrganizationMemberListResponse;
import com.clickchecker.organizationmember.mapper.AdminOrganizationMemberResponseMapper;
import com.clickchecker.organizationmember.service.OrganizationMemberQueryService;
import com.clickchecker.web.resolver.CurrentAccountId;
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
    private final AdminOrganizationMemberResponseMapper adminOrganizationMemberResponseMapper;

    @GetMapping
    public AdminOrganizationMemberListResponse getMembers(
            @CurrentAccountId Long accountId,
            @PathVariable Long organizationId
    ) {
        return adminOrganizationMemberResponseMapper.toListResponse(
                organizationMemberQueryService.getMembers(accountId, organizationId)
        );
    }
}
