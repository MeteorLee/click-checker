package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record RawOccurredAtCountProjection(
        Instant occurredAt,
        long count
) {
}
