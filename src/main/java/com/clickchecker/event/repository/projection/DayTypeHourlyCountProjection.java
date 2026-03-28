package com.clickchecker.event.repository.projection;

public record DayTypeHourlyCountProjection(
        String dayType,
        int hourOfDay,
        long eventCount
) {
}
