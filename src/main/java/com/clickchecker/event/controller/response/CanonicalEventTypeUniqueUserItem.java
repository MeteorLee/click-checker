package com.clickchecker.event.controller.response;

public record CanonicalEventTypeUniqueUserItem(
        String canonicalEventType,
        long uniqueUsers
) {
}
