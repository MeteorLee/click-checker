package com.clickchecker.analytics.aggregate.controller.response;

public record CountResponse(
        String eventType,
        Long count
) {
}
