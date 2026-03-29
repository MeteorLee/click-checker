package com.clickchecker.analytics.activity.controller.response;

import java.time.Instant;

public record AdminActivityDailyItemResponse(
        Instant bucketStart,
        long eventCount,
        long uniqueUserCount
) {
}
