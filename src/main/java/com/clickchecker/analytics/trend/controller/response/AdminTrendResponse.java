package com.clickchecker.analytics.trend.controller.response;

import com.clickchecker.analytics.common.model.TimeBucket;
import java.time.Instant;
import java.util.List;

public record AdminTrendResponse(
        Long organizationId,
        Instant from,
        Instant to,
        String timezone,
        TimeBucket bucket,
        List<AdminTrendPointResponse> eventCounts,
        List<AdminTrendPointResponse> uniqueUserCounts
) {
}
