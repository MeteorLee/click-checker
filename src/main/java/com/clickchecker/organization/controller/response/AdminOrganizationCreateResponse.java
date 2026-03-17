package com.clickchecker.organization.controller.response;

public record AdminOrganizationCreateResponse(
        Long organizationId,
        String name,
        Long ownerMembershipId
) {
}
