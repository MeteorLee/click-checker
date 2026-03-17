package com.clickchecker.analytics.trend.controller.response;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;

import java.time.Instant;
import java.util.List;

public record TimeBucketAggregateResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        String eventType,
        String timezone,
        TimeBucket bucket,
        List<TimeBucketCountProjection> items
) {
}
