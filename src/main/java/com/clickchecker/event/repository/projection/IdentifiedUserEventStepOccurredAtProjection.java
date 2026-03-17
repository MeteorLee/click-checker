package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record IdentifiedUserEventStepOccurredAtProjection(
        Long eventUserId,
        String rawEventType,
        String path,
        Instant occurredAt
) {
}
