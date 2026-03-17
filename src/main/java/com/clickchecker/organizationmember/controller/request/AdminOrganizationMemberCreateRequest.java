package com.clickchecker.organizationmember.controller.request;

import com.clickchecker.organizationmember.entity.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record AdminOrganizationMemberCreateRequest(
        @NotNull
        Long accountId,

        @NotNull
        OrganizationRole role
) {
}
