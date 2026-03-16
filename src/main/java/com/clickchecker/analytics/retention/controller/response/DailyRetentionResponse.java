package com.clickchecker.analytics.retention.controller.response;

import java.time.Instant;
import java.util.List;

public record DailyRetentionResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        String timezone,
        List<DailyRetentionItem> items
) {
}
