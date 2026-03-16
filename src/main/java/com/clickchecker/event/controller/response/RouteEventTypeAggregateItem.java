package com.clickchecker.event.controller.response;

public record RouteEventTypeAggregateItem(
        String routeKey,
        String canonicalEventType,
        long count
) {
}
