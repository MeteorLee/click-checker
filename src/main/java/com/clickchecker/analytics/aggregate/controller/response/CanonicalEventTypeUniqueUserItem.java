package com.clickchecker.analytics.aggregate.controller.response;

public record CanonicalEventTypeUniqueUserItem(
        String canonicalEventType,
        long uniqueUsers
) {
}
