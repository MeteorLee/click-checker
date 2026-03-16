package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record RawPathTimeBucketCountProjection(
        String path,
        Instant bucketStart,
        long count
) {
}
