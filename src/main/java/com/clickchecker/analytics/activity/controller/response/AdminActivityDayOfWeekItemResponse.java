package com.clickchecker.analytics.activity.controller.response;

public record AdminActivityDayOfWeekItemResponse(
        int dayOfWeek,
        long eventCount,
        long uniqueUserCount
) {
}
