package com.clickchecker.event.controller.response;

public record RawEventTypeItem(
        String rawEventType,
        long count
) {
}
