package com.clickchecker.event.repository.dto;

import java.time.Instant;

public record TimeBucketCountDto(
        Instant bucketStart,
        long count
) {
}
