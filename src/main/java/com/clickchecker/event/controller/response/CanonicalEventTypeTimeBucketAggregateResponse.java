package com.clickchecker.event.controller.response;

import com.clickchecker.event.model.TimeBucket;

import java.time.Instant;
import java.util.List;

public record CanonicalEventTypeTimeBucketAggregateResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        TimeBucket bucket,
        List<CanonicalEventTypeTimeBucketItem> items
) {
}
