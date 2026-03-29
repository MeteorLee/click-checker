package com.clickchecker.analytics.retention.service;

import com.clickchecker.analytics.retention.controller.response.RetentionMatrixResponse;
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

class RetentionMatrixServiceTest {

    private final EventQueryRepository eventQueryRepository = mock(EventQueryRepository.class);
    private final RetentionAnalyticsService retentionAnalyticsService =
            new RetentionAnalyticsService(eventQueryRepository);

    @Test
    void getRetentionMatrix_returnsCustomDayValuesPerCohort() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-08T00:00:00Z");

        when(eventQueryRepository.findIdentifiedUserFirstSeen(1L, null))
                .thenReturn(List.of(
                        new IdentifiedUserFirstSeenProjection(101L, Instant.parse("2026-03-01T01:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(102L, Instant.parse("2026-03-01T12:00:00Z"))
                ));

        when(eventQueryRepository.findIdentifiedUserOccurredAtBetween(
                from,
                Instant.parse("2026-03-16T00:00:00Z"),
                1L,
                null
        )).thenReturn(List.of(
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-01T01:00:00Z")),
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-02T03:00:00Z")),
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-04T03:00:00Z")),
                new IdentifiedUserOccurredAtProjection(102L, Instant.parse("2026-03-01T12:00:00Z")),
                new IdentifiedUserOccurredAtProjection(102L, Instant.parse("2026-03-08T12:00:00Z"))
        ));

        RetentionMatrixResponse result = retentionAnalyticsService.getRetentionMatrix(
                from,
                to,
                ZoneId.of("Asia/Seoul"),
                1L,
                null,
                List.of(1, 3, 7),
                1
        );

        assertThat(result.days()).containsExactly(1, 3, 7);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().cohortUsers()).isEqualTo(2);
        assertThat(result.items().getFirst().values()).hasSize(3);

        assertThat(result.items().getFirst().values().get(0).day()).isEqualTo(1);
        assertThat(result.items().getFirst().values().get(0).users()).isEqualTo(1);
        assertThat(result.items().getFirst().values().get(0).retentionRate()).isEqualTo(0.5);

        assertThat(result.items().getFirst().values().get(1).day()).isEqualTo(3);
        assertThat(result.items().getFirst().values().get(1).users()).isEqualTo(1);
        assertThat(result.items().getFirst().values().get(1).retentionRate()).isEqualTo(0.5);

        assertThat(result.items().getFirst().values().get(2).day()).isEqualTo(7);
        assertThat(result.items().getFirst().values().get(2).users()).isEqualTo(2);
        assertThat(result.items().getFirst().values().get(2).retentionRate()).isEqualTo(1.0);
    }

    @Test
    void getRetentionMatrix_filtersOutSmallCohorts_usingMinCohortUsers() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-08T00:00:00Z");

        when(eventQueryRepository.findIdentifiedUserFirstSeen(1L, null))
                .thenReturn(List.of(
                        new IdentifiedUserFirstSeenProjection(101L, Instant.parse("2026-03-01T01:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(102L, Instant.parse("2026-03-01T12:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(201L, Instant.parse("2026-03-02T01:00:00Z"))
                ));

        when(eventQueryRepository.findIdentifiedUserOccurredAtBetween(
                from,
                Instant.parse("2026-03-09T00:00:00Z"),
                1L,
                null
        )).thenReturn(List.of(
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-01T01:00:00Z")),
                new IdentifiedUserOccurredAtProjection(101L, Instant.parse("2026-03-02T03:00:00Z")),
                new IdentifiedUserOccurredAtProjection(102L, Instant.parse("2026-03-01T12:00:00Z")),
                new IdentifiedUserOccurredAtProjection(201L, Instant.parse("2026-03-02T01:00:00Z")),
                new IdentifiedUserOccurredAtProjection(201L, Instant.parse("2026-03-03T01:00:00Z"))
        ));

        RetentionMatrixResponse result = retentionAnalyticsService.getRetentionMatrix(
                from,
                to,
                ZoneId.of("Asia/Seoul"),
                1L,
                null,
                List.of(1),
                2
        );

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().cohortDate()).isEqualTo(java.time.LocalDate.parse("2026-03-01"));
        assertThat(result.items().getFirst().cohortUsers()).isEqualTo(2);
    }
}
