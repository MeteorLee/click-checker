package com.clickchecker.analytics.aggregate.controller.response;

import java.time.Instant;
import java.util.List;

public record RouteEventTypeAggregateResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        Integer top,
        List<RouteEventTypeAggregateItem> items
) {
}
