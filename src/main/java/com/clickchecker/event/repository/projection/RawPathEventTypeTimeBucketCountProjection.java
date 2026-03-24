package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record RawPathEventTypeTimeBucketCountProjection(
        String path,
        String rawEventType,
        Instant bucketStart,
        long count
) {
}
