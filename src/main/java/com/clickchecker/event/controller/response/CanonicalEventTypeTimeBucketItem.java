package com.clickchecker.event.controller.response;

import java.time.Instant;

public record CanonicalEventTypeTimeBucketItem(
        String canonicalEventType,
        Instant bucketStart,
        long count
) {
}
