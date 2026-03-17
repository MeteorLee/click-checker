package com.clickchecker.organizationmember.controller.response;

import java.util.List;

public record AdminOrganizationMemberListResponse(
        List<AdminOrganizationMemberResponse> members
) {
}
