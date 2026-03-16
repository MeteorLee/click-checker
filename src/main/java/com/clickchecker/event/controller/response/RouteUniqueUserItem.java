package com.clickchecker.event.controller.response;

public record RouteUniqueUserItem(
        String routeKey,
        long uniqueUsers
) {
}
