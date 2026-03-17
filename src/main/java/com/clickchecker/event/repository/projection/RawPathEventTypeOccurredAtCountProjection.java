package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record RawPathEventTypeOccurredAtCountProjection(
        String path,
        String rawEventType,
        Instant occurredAt,
        long count
) {
}
