package com.clickchecker.event.repository.support;

import com.clickchecker.analytics.common.model.TimeBucket;

public final class TimeBucketSql {

    private TimeBucketSql() {
    }

    public static String epochBucketStartExpression(String columnExpression, TimeBucket bucket, String timezone) {
        String timezoneLiteral = "'" + timezone.replace("'", "''") + "'";
        String unit = switch (bucket) {
            case HOUR -> "hour";
            case DAY -> "day";
        };
        return "extract(epoch from date_trunc('" + unit + "', " + columnExpression + ", " + timezoneLiteral + "))";
    }
}
