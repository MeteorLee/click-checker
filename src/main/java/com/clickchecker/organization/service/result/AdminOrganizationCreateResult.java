package com.clickchecker.organization.service.result;

public record AdminOrganizationCreateResult(
        Long organizationId,
        String name,
        Long ownerMembershipId,
        String apiKey,
        String apiKeyPrefix
) {
}
