package com.clickchecker.event.repository.projection;

public record DayOfWeekCountProjection(
        int dayOfWeek,
        long eventCount,
        long uniqueUserCount
) {
}
