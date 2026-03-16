package com.clickchecker.event.controller.response;

import java.time.Instant;
import java.util.List;

public record RouteAggregateResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        String eventType,
        int top,
        List<RouteAggregateItem> items
) {
}
