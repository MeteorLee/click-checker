package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record TimeBucketCountProjection(
        Instant bucketStart,
        long count
) {
}
