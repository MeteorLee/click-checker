package com.clickchecker.event.controller.response;

import java.time.Instant;

public record RouteTimeBucketItem(
        String routeKey,
        Instant bucketStart,
        long count
) {
}
