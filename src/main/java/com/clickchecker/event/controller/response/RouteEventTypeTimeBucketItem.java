package com.clickchecker.event.controller.response;

import java.time.Instant;

public record RouteEventTypeTimeBucketItem(
        String routeKey,
        String canonicalEventType,
        Instant bucketStart,
        long count
) {
}
