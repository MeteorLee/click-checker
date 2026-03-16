package com.clickchecker.event.controller.response;

public record CanonicalEventTypeItem(
        String canonicalEventType,
        long count
) {
}
