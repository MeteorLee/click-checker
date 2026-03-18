package com.clickchecker.organizationmember.controller;

import com.clickchecker.organizationmember.controller.request.AdminOrganizationMemberCreateRequest;
import com.clickchecker.organizationmember.controller.request.AdminOrganizationMemberUpdateRoleRequest;
import com.clickchecker.organizationmember.controller.response.AdminOrganizationMemberListResponse;
import com.clickchecker.organizationmember.mapper.AdminOrganizationMemberResponseMapper;
import com.clickchecker.organizationmember.controller.response.AdminOrganizationMemberResponse;
import com.clickchecker.organizationmember.service.OrganizationMemberCommandService;
import com.clickchecker.organizationmember.service.OrganizationMemberQueryService;
import com.clickchecker.web.resolver.CurrentAccountId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/organizations/{organizationId}/members")
public class AdminOrganizationMemberController {

    private final OrganizationMemberCommandService organizationMemberCommandService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AdminOrganizationMemberResponseMapper adminOrganizationMemberResponseMapper;

    @PostMapping
    public ResponseEntity<AdminOrganizationMemberResponse> addMember(
            @CurrentAccountId Long accountId,
            @PathVariable Long organizationId,
            @RequestBody @Valid AdminOrganizationMemberCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                adminOrganizationMemberResponseMapper.toResponse(
                        organizationMemberCommandService.addMember(
                                accountId,
                                organizationId,
                                request.accountId(),
                                request.role()
                        )
                )
        );
    }

    @PutMapping("/{memberId}/role")
    public AdminOrganizationMemberResponse updateRole(
            @CurrentAccountId Long accountId,
            @PathVariable Long organizationId,
            @PathVariable Long memberId,
            @RequestBody @Valid AdminOrganizationMemberUpdateRoleRequest request
    ) {
        return adminOrganizationMemberResponseMapper.toResponse(
                organizationMemberCommandService.updateRole(
                        accountId,
                        organizationId,
                        memberId,
                        request.role()
                )
        );
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(
            @CurrentAccountId Long accountId,
            @PathVariable Long organizationId,
            @PathVariable Long memberId
    ) {
        organizationMemberCommandService.removeMember(accountId, organizationId, memberId);
        return ResponseEntity.noContent().build();
    }

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
