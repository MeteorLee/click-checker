package com.clickchecker.analytics.aggregate.controller.response;

import java.time.Instant;
import java.util.List;

public record CanonicalEventTypeUniqueUserAggregateResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        int top,
        List<CanonicalEventTypeUniqueUserItem> items
) {
}
