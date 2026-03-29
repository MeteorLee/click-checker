package com.clickchecker.organizationmember.controller;

import com.clickchecker.organizationmember.controller.request.AdminOrganizationMemberCreateRequest;
import com.clickchecker.organizationmember.controller.request.AdminOrganizationMemberInviteRequest;
import com.clickchecker.organizationmember.controller.request.AdminOrganizationLeaveRequest;
import com.clickchecker.organizationmember.controller.request.AdminOrganizationMemberUpdateRoleRequest;
import com.clickchecker.organizationmember.controller.response.AdminOrganizationMemberListResponse;
import com.clickchecker.organizationmember.mapper.AdminOrganizationMemberResponseMapper;
import com.clickchecker.organizationmember.controller.response.AdminOrganizationMemberResponse;
import com.clickchecker.organizationmember.service.OrganizationMemberCommandService;
import com.clickchecker.organizationmember.service.OrganizationMemberQueryService;
import com.clickchecker.security.principal.AdminPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestBody @Valid AdminOrganizationMemberCreateRequest request
    ) {
        Long accountId = principal.accountId();
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

    @PostMapping("/by-login-id")
    public ResponseEntity<AdminOrganizationMemberResponse> addMemberByLoginId(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestBody @Valid AdminOrganizationMemberInviteRequest request
    ) {
        Long accountId = principal.accountId();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                adminOrganizationMemberResponseMapper.toResponse(
                        organizationMemberCommandService.addMemberByLoginId(
                                accountId,
                                organizationId,
                                request.loginId(),
                                request.role()
                        )
                )
        );
    }

    @PutMapping("/{memberId}/role")
    public AdminOrganizationMemberResponse updateRole(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @PathVariable Long memberId,
            @RequestBody @Valid AdminOrganizationMemberUpdateRoleRequest request
    ) {
        Long accountId = principal.accountId();
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
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @PathVariable Long memberId
    ) {
        Long accountId = principal.accountId();
        organizationMemberCommandService.removeMember(accountId, organizationId, memberId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/membership")
    public ResponseEntity<Void> leaveOrganization(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestBody(required = false) AdminOrganizationLeaveRequest request
    ) {
        Long accountId = principal.accountId();
        organizationMemberCommandService.leaveOrganization(
                accountId,
                organizationId,
                request == null ? null : request.confirmationText()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public AdminOrganizationMemberListResponse getMembers(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId
    ) {
        Long accountId = principal.accountId();
        return adminOrganizationMemberResponseMapper.toListResponse(
                organizationMemberQueryService.getMembers(accountId, organizationId)
        );
    }
}
