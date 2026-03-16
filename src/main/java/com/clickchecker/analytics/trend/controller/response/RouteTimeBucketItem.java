package com.clickchecker.analytics.trend.controller.response;

import java.time.Instant;

public record RouteTimeBucketItem(
        String routeKey,
        Instant bucketStart,
        long count
) {
}
