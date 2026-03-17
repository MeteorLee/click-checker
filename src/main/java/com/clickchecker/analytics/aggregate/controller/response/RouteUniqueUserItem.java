package com.clickchecker.analytics.aggregate.controller.response;

public record RouteUniqueUserItem(
        String routeKey,
        long uniqueUsers
) {
}
