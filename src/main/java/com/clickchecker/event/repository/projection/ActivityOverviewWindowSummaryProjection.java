package com.clickchecker.event.repository.projection;

public record ActivityOverviewWindowSummaryProjection(
        long currentTotalEvents,
        long currentUniqueUsers,
        long currentIdentifiedEvents,
        long previousTotalEvents
) {
}
