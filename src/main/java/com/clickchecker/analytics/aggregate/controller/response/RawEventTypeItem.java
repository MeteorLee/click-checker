package com.clickchecker.analytics.aggregate.controller.response;

public record RawEventTypeItem(
        String rawEventType,
        long count
) {
}
