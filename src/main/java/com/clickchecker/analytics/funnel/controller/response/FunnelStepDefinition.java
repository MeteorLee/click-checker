package com.clickchecker.analytics.funnel.controller.response;

public record FunnelStepDefinition(
        String canonicalEventType,
        String routeKey
) {
}
