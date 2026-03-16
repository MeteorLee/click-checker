package com.clickchecker.analytics.aggregate.controller.response;

public record RouteAggregateItem(
        String routeKey,
        long count
) {
}
