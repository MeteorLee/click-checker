package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record RawEventTypeOccurredAtCountProjection(
        String rawEventType,
        Instant occurredAt,
        long count
) {
}
