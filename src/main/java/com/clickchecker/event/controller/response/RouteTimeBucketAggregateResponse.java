package com.clickchecker.event.controller.response;

import com.clickchecker.event.model.TimeBucket;
import java.time.Instant;
import java.util.List;

public record RouteTimeBucketAggregateResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        String eventType,
        String timezone,
        TimeBucket bucket,
        List<RouteTimeBucketItem> items
) {
}
