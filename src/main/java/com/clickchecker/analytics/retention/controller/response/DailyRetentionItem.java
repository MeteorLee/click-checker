package com.clickchecker.analytics.retention.controller.response;

import java.time.LocalDate;

public record DailyRetentionItem(
        LocalDate cohortDate,
        long cohortUsers,
        long day1Users,
        Double day1RetentionRate,
        long day7Users,
        Double day7RetentionRate,
        long day30Users,
        Double day30RetentionRate
) {
}
