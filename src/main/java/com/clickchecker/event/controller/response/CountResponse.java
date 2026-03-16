package com.clickchecker.event.controller.response;

public record CountResponse(
        String eventType,
        Long count
) {
}
