package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record RawEventTypeTimeBucketCountProjection(
        String rawEventType,
        Instant bucketStart,
        long count
) {
}
