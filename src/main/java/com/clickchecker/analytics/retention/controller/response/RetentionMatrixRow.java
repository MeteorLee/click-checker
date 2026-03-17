package com.clickchecker.analytics.retention.controller.response;

import java.time.LocalDate;
import java.util.List;

public record RetentionMatrixRow(
        LocalDate cohortDate,
        long cohortUsers,
        List<RetentionMatrixValue> values
) {
}
