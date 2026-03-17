package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record IdentifiedUserOccurredAtProjection(
        Long eventUserId,
        Instant occurredAt
) {
}
