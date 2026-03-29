package com.clickchecker.analytics.trend.controller.response;

import java.time.Instant;

public record AdminTrendPointResponse(
        Instant bucketStart,
        long count
) {
}
