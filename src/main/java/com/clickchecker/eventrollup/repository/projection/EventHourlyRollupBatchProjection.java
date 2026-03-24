package com.clickchecker.eventrollup.repository.projection;

import java.time.Instant;

public record EventHourlyRollupBatchProjection(
        Long organizationId,
        Instant bucketStart,
        String path,
        String eventType,
        long eventCount,
        long identifiedEventCount,
        Instant maxCreatedAt
) {
}
