package com.clickchecker.analytics.funnel.controller.response;

import java.time.Instant;
import java.util.List;

public record FunnelReportResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        List<FunnelStepDefinition> steps,
        String conversionWindow,
        List<FunnelStepResult> items
) {
}
