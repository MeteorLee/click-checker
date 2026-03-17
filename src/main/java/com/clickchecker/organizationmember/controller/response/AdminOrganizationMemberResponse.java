package com.clickchecker.organizationmember.controller.response;

public record AdminOrganizationMemberResponse(
        Long memberId,
        Long accountId,
        String loginId,
        String accountStatus,
        String role
) {
}
