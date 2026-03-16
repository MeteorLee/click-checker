package com.clickchecker.event.repository.projection;

public record IdentifiedUserEventCountProjection(
        Long eventUserId,
        long eventCount
) {
}
