package com.clickchecker.analytics.activity.controller.response;

public record AdminActivityHourlyItemResponse(
        int hourOfDay,
        long eventCount
) {
}
