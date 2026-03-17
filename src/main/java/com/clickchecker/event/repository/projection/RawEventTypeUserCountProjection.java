package com.clickchecker.event.repository.projection;

public record RawEventTypeUserCountProjection(
        String rawEventType,
        Long eventUserId,
        Long count
) {
}
