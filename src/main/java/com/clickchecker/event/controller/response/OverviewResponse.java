package com.clickchecker.event.controller.response;

import java.time.Instant;
import java.util.List;

public record OverviewResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        String eventType,
        long totalEvents,
        Long uniqueUsers,
        Double identifiedEventRate,
        Double eventTypeMappingCoverage,
        Double routeMatchCoverage,
        Comparison comparison,
        List<RouteSummary> topRoutes,
        List<EventTypeSummary> topEventTypes
) {

    public record Comparison(
            long current,
            long previous,
            long delta,
            Double deltaRate,
            boolean hasPreviousBaseline
    ) {
    }

    public record RouteSummary(
            String routeKey,
            long count
    ) {
    }

    public record EventTypeSummary(
            String eventType,
            long count
    ) {
    }
}
