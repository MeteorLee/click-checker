package com.clickchecker.analytics.retention.service;

import com.clickchecker.analytics.retention.controller.response.DailyRetentionResponse;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.IdentifiedUserFirstSeenProjection;
import com.clickchecker.event.repository.projection.IdentifiedUserOccurredAtProjection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetentionAnalyticsServiceTest {

    private final EventQueryRepository eventQueryRepository = mock(EventQueryRepository.class);
    private final RetentionAnalyticsService retentionAnalyticsService =
            new RetentionAnalyticsService(eventQueryRepository);

    @Test
    void getDailyRetention_groupsByTimezoneLocalDate_andCalculatesWithinDayRetention() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-08T00:00:00Z");

        when(eventQueryRepository.findIdentifiedUserFirstSeen(1L, null))
                .thenReturn(List.of(
                        new IdentifiedUserFirstSeenProjection(101L, Instant.parse("2026-03-01T01:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(102L, Instant.parse("2026-03-01T12:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(103L, Instant.parse("2026-03-02T01:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(104L, Instant.parse("2026-02-28T23:00:00Z"))
                ));

        when(eventQueryRepository.findIdentifiedUserOccurredAtBetween(
                from,
                Instant.parse("2026-04-08T00:00:00Z"),
                1L,
                null
        )).thenReturn(List.of(
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-01T01:00:00Z")),
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-02T03:00:00Z")),
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-08T05:00:00Z")),
                new IdentifiedUserOccurredAtProjection(102L, Instant.parse("2026-03-01T12:00:00Z")),
                new IdentifiedUserOccurredAtProjection(102L, Instant.parse("2026-03-31T12:00:00Z")),
                new IdentifiedUserOccurredAtProjection(103L, Instant.parse("2026-03-02T01:00:00Z"))
        ));

        DailyRetentionResponse result = retentionAnalyticsService.getDailyRetention(
                from,
                to,
                ZoneId.of("Asia/Seoul"),
                1L,
                null,
                1
        );

        assertThat(result.timezone()).isEqualTo("Asia/Seoul");
        assertThat(result.items()).hasSize(2);

        assertThat(result.items().get(0).cohortDate().toString()).isEqualTo("2026-03-01");
        assertThat(result.items().get(0).cohortUsers()).isEqualTo(2);
        assertThat(result.items().get(0).day1Users()).isEqualTo(1);
        assertThat(result.items().get(0).day1RetentionRate()).isEqualTo(0.5);
        assertThat(result.items().get(0).day7Users()).isEqualTo(1);
        assertThat(result.items().get(0).day7RetentionRate()).isEqualTo(0.5);
        assertThat(result.items().get(0).day30Users()).isEqualTo(2);
        assertThat(result.items().get(0).day30RetentionRate()).isEqualTo(1.0);

        assertThat(result.items().get(1).cohortDate().toString()).isEqualTo("2026-03-02");
        assertThat(result.items().get(1).cohortUsers()).isEqualTo(1);
        assertThat(result.items().get(1).day1Users()).isEqualTo(0);
        assertThat(result.items().get(1).day7Users()).isEqualTo(0);
        assertThat(result.items().get(1).day30Users()).isEqualTo(0);
    }

    @Test
    void getDailyRetention_filtersOutSmallCohorts_usingMinCohortUsers() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-08T00:00:00Z");

        when(eventQueryRepository.findIdentifiedUserFirstSeen(1L, null))
                .thenReturn(List.of(
                        new IdentifiedUserFirstSeenProjection(101L, Instant.parse("2026-03-01T01:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(102L, Instant.parse("2026-03-01T12:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(103L, Instant.parse("2026-03-02T01:00:00Z"))
                ));

        when(eventQueryRepository.findIdentifiedUserOccurredAtBetween(
                from,
                Instant.parse("2026-04-08T00:00:00Z"),
                1L,
                null
        )).thenReturn(List.of(
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-01T01:00:00Z")),
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-02T03:00:00Z")),
                new IdentifiedUserOccurredAtProjection(102L, Instant.parse("2026-03-01T12:00:00Z")),
                new IdentifiedUserOccurredAtProjection(103L, Instant.parse("2026-03-02T01:00:00Z"))
        ));

        DailyRetentionResponse result = retentionAnalyticsService.getDailyRetention(
                from,
                to,
                ZoneId.of("Asia/Seoul"),
                1L,
                null,
                2
        );

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().cohortDate().toString()).isEqualTo("2026-03-01");
        assertThat(result.items().getFirst().cohortUsers()).isEqualTo(2);
    }
}
