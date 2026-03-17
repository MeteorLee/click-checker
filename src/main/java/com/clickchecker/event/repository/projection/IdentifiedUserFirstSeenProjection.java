package com.clickchecker.event.repository.projection;

import java.time.Instant;

public record IdentifiedUserFirstSeenProjection(
        Long eventUserId,
        Instant firstSeenAt
) {
}
