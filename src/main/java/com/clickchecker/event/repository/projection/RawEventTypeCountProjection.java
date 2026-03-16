package com.clickchecker.event.repository.projection;

public record RawEventTypeCountProjection(
        String rawEventType,
        long count
) {
}
