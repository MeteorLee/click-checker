package com.clickchecker.event.repository.projection;

public record RawEventTypeUserProjection(
        String rawEventType,
        Long eventUserId
) {
}
