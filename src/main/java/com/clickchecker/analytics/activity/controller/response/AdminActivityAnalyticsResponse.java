package com.clickchecker.analytics.activity.controller.response;

import java.time.Instant;
import java.util.List;

public record AdminActivityAnalyticsResponse(
        Long organizationId,
        Instant from,
        Instant to,
        String timezone,
        long totalEvents,
        double averageEventsPerDay,
        long activeDays,
        Instant peakDayBucketStart,
        long peakDayEventCount,
        AdminActivityDayTypeSummaryResponse weekdaySummary,
        AdminActivityDayTypeSummaryResponse weekendSummary,
        List<AdminActivityDayOfWeekItemResponse> dayOfWeekDistribution,
        List<AdminActivityDailyItemResponse> dailyActivity,
        List<AdminActivityHourlyItemResponse> hourlyDistribution,
        List<AdminActivityHourlyItemResponse> weekdayHourlyDistribution,
        List<AdminActivityHourlyItemResponse> weekendHourlyDistribution
) {
}
