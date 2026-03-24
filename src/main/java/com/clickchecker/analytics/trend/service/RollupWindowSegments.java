package com.clickchecker.analytics.trend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public record RollupWindowSegments(
        TimeRange leadingPartial,
        TimeRange fullHours,
        TimeRange trailingPartial
) {

    public static RollupWindowSegments of(Instant from, Instant to) {
        Instant fullHoursStart = ceilToUtcHour(from);
        Instant fullHoursEnd = floorToUtcHour(to);

        if (!fullHoursStart.isBefore(fullHoursEnd)) {
            return new RollupWindowSegments(new TimeRange(from, to), null, null);
        }

        TimeRange leadingPartial = from.isBefore(fullHoursStart)
                ? new TimeRange(from, fullHoursStart)
                : null;
        TimeRange fullHours = new TimeRange(fullHoursStart, fullHoursEnd);
        TimeRange trailingPartial = fullHoursEnd.isBefore(to)
                ? new TimeRange(fullHoursEnd, to)
                : null;

        return new RollupWindowSegments(leadingPartial, fullHours, trailingPartial);
    }

    public boolean hasFullHours() {
        return fullHours != null && fullHours.from().isBefore(fullHours.to());
    }

    public List<TimeRange> rawSegments() {
        List<TimeRange> segments = new ArrayList<>();
        if (leadingPartial != null) {
            segments.add(leadingPartial);
        }
        if (trailingPartial != null) {
            segments.add(trailingPartial);
        }
        return segments;
    }

    private static Instant ceilToUtcHour(Instant instant) {
        Instant floored = floorToUtcHour(instant);
        return floored.equals(instant) ? instant : floored.plus(1, ChronoUnit.HOURS);
    }

    private static Instant floorToUtcHour(Instant instant) {
        return instant.truncatedTo(ChronoUnit.HOURS);
    }

    public record TimeRange(
            Instant from,
            Instant to
    ) {
    }
}
