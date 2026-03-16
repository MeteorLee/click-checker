package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record IdentifiedUserEventTypeOccurredAtProjection(
        Long eventUserId,
        String rawEventType,
        Instant occurredAt
) {
}
