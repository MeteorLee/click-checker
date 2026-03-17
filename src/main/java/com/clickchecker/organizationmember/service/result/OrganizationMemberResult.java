package com.clickchecker.organizationmember.service.result;

public record OrganizationMemberResult(
        Long memberId,
        Long accountId,
        String loginId,
        String accountStatus,
        String role
) {
}
