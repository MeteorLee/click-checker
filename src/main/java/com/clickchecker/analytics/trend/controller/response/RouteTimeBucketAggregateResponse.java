package com.clickchecker.analytics.trend.controller.response;

import com.clickchecker.analytics.common.model.TimeBucket;
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
