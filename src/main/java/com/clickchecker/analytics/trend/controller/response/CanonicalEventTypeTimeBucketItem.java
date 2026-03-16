package com.clickchecker.analytics.trend.controller.response;

import java.time.Instant;

public record CanonicalEventTypeTimeBucketItem(
        String canonicalEventType,
        Instant bucketStart,
        long count
) {
}
