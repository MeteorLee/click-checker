package com.clickchecker.event.repository.projection;

public record RawPathEventTypeCountProjection(
        String path,
        String rawEventType,
        long count
) {
}
