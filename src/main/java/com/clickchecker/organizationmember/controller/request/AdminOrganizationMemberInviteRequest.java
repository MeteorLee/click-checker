package com.clickchecker.organizationmember.controller.request;

import com.clickchecker.organizationmember.entity.OrganizationRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminOrganizationMemberInviteRequest(
        @NotBlank
        String loginId,

        @NotNull
        OrganizationRole role
) {
}
