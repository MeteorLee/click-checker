package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record RawPathOccurredAtCountProjection(
        String path,
        Instant occurredAt,
        long count
) {
}
