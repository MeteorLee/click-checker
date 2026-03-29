package com.clickchecker.analytics.activity.controller.response;

public record AdminActivityDayTypeSummaryResponse(
        long eventCount,
        long uniqueUserCount,
        double averageEventsPerDay,
        double averageUniqueUsersPerDay
) {
}
