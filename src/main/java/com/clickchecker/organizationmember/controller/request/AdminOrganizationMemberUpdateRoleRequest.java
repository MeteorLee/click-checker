package com.clickchecker.organizationmember.controller.request;

import com.clickchecker.organizationmember.entity.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record AdminOrganizationMemberUpdateRoleRequest(
        @NotNull
        OrganizationRole role
) {
}
