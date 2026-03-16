package com.clickchecker.eventtype.controller.response;

import java.util.List;

public record EventTypeMappingListResponse(
        List<EventTypeMappingItem> items
) {
}
