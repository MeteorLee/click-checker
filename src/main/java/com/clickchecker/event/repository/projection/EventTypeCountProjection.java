package com.clickchecker.event.repository.projection;

public record EventTypeCountProjection(
        String eventType,
        long count
) {
}
