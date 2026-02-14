package com.clickchecker.event.repository.dto;

import java.time.LocalDateTime;

public record TimeBucketCountDto(
        LocalDateTime bucketStart,
        long count
) {
}
