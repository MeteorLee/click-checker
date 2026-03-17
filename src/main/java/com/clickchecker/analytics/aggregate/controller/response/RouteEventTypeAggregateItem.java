package com.clickchecker.analytics.aggregate.controller.response;

public record RouteEventTypeAggregateItem(
        String routeKey,
        String canonicalEventType,
        long count
) {
}
