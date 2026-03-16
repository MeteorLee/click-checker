package com.clickchecker.analytics.user.controller.response;

import java.time.Instant;

public record UserAnalyticsOverviewResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        Long identifiedUsers,
        Long newUsers,
        Long returningUsers,
        Double avgEventsPerIdentifiedUser
) {
}
