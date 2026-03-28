package com.clickchecker.analytics.user.controller.response;

import java.time.Instant;

public record UserAnalyticsOverviewResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        Long totalEvents,
        Long identifiedEvents,
        Long anonymousEvents,
        Long identifiedUsers,
        Long newUsers,
        Long returningUsers,
        Long newUserEvents,
        Long returningUserEvents,
        Double avgEventsPerIdentifiedUser
) {
}
