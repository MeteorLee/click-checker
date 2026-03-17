package com.clickchecker.analytics.aggregate.controller.response;

public record CanonicalEventTypeItem(
        String canonicalEventType,
        long count
) {
}
