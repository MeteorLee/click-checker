package com.clickchecker.event.controller.response;

import com.clickchecker.event.repository.projection.PathCountProjection;

import java.time.Instant;
import java.util.List;

public record PathAggregateResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        String eventType,
        int top,
        List<PathCountProjection> items
) {
}
