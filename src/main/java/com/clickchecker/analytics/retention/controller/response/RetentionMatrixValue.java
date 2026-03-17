package com.clickchecker.analytics.retention.controller.response;

public record RetentionMatrixValue(
        int day,
        long users,
        Double retentionRate
) {
}
