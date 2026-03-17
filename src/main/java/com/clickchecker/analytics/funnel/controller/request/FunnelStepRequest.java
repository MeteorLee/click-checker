package com.clickchecker.analytics.funnel.controller.request;

import jakarta.validation.constraints.NotBlank;

public record FunnelStepRequest(
        @NotBlank String canonicalEventType,
        String routeKey
) {
}
