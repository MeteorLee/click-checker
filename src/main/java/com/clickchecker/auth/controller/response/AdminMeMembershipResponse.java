package com.clickchecker.auth.controller.response;

public record AdminMeMembershipResponse(
        Long membershipId,
        Long organizationId,
        String organizationName,
        String role
) {
}
