package com.clickchecker.eventtype.controller.response;

public record EventTypeMappingItem(
        Long id,
        String rawEventType,
        String canonicalEventType,
        boolean active
) {
}
