package com.clickchecker.analytics.retention.controller.response;

import java.time.Instant;
import java.util.List;

public record RetentionMatrixResponse(
        Long organizationId,
        String externalUserId,
        Instant from,
        Instant to,
        String timezone,
        List<Integer> days,
        List<RetentionMatrixRow> items
) {
}
