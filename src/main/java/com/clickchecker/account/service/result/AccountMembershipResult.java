package com.clickchecker.account.service.result;

public record AccountMembershipResult(
        Long membershipId,
        Long organizationId,
        String organizationName,
        String role
) {
}
