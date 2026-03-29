package com.clickchecker.event.repository.projection;

public record DayTypeSummaryProjection(
        String dayType,
        long eventCount,
        long uniqueUserCount
) {
}
