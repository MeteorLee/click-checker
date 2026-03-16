package com.clickchecker.event.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public enum TimeBucket {
    HOUR {
        @Override
        public Instant floor(Instant instant, ZoneId zoneId) {
            ZonedDateTime zonedDateTime = instant.atZone(zoneId);
            return zonedDateTime
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
                    .toInstant();
        }

        @Override
        public Instant next(Instant bucketStart, ZoneId zoneId) {
            return bucketStart.atZone(zoneId)
                    .plusHours(1)
                    .toInstant();
        }
    },
    DAY {
        @Override
        public Instant floor(Instant instant, ZoneId zoneId) {
            return instant.atZone(zoneId)
                    .toLocalDate()
                    .atStartOfDay(zoneId)
                    .toInstant();
        }

        @Override
        public Instant next(Instant bucketStart, ZoneId zoneId) {
            return bucketStart.atZone(zoneId)
                    .plusDays(1)
                    .toInstant();
        }
    };

    public abstract Instant floor(Instant instant, ZoneId zoneId);

    public abstract Instant next(Instant bucketStart, ZoneId zoneId);
}
