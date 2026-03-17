package com.clickchecker.auth.controller.response;

import java.util.List;

public record AdminMeResponse(
        Long accountId,
        String loginId,
        String status,
        List<AdminMeMembershipResponse> memberships
) {
}
