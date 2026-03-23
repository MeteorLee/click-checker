package com.clickchecker.eventrollup.service;

import com.clickchecker.eventrollup.entity.EventRollupWatermark;
import com.clickchecker.eventrollup.repository.EventHourlyRollupCommandRepository;
import com.clickchecker.eventrollup.repository.EventRollupSourceQueryRepository;
import com.clickchecker.eventrollup.repository.EventRollupWatermarkRepository;
import com.clickchecker.eventrollup.repository.projection.EventHourlyRollupBatchProjection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventRollupServiceTest {

    private final EventRollupWatermarkRepository eventRollupWatermarkRepository = mock(EventRollupWatermarkRepository.class);
    private final EventRollupSourceQueryRepository eventRollupSourceQueryRepository = mock(EventRollupSourceQueryRepository.class);
    private final EventHourlyRollupCommandRepository eventHourlyRollupCommandRepository = mock(EventHourlyRollupCommandRepository.class);

    private final EventRollupService eventRollupService = new EventRollupService(
            eventRollupWatermarkRepository,
            eventRollupSourceQueryRepository,
            eventHourlyRollupCommandRepository
    );

    @Test
    void refreshOrganizationHourlyRollups_updatesExistingWatermarkAfterUpsert() {
        Instant processedCreatedAt = Instant.parse("2026-03-23T00:00:00Z");
        Instant maxCreatedAt = Instant.parse("2026-03-23T00:30:00Z");
        EventRollupWatermark watermark = new EventRollupWatermark(20L, processedCreatedAt);

        when(eventRollupWatermarkRepository.findById(20L)).thenReturn(Optional.of(watermark));
        when(eventRollupSourceQueryRepository.findHourlyRollupBatchesAfter(20L, processedCreatedAt))
                .thenReturn(List.of(
                        new EventHourlyRollupBatchProjection(
                                20L,
                                Instant.parse("2026-03-23T00:00:00Z"),
                                "/pricing",
                                "view",
                                10L,
                                7L,
                                maxCreatedAt
                        )
                ));

        EventRollupRefreshResult result = eventRollupService.refreshOrganizationHourlyRollups(20L);

        verify(eventHourlyRollupCommandRepository).upsertAll(List.of(
                new EventHourlyRollupBatchProjection(
                        20L,
                        Instant.parse("2026-03-23T00:00:00Z"),
                        "/pricing",
                        "view",
                        10L,
                        7L,
                        maxCreatedAt
                )
        ));
        verify(eventRollupWatermarkRepository).save(watermark);
        assertThat(watermark.getProcessedCreatedAt()).isEqualTo(maxCreatedAt);
        assertThat(result).isEqualTo(new EventRollupRefreshResult(20L, 1, maxCreatedAt));
    }

    @Test
    void refreshOrganizationHourlyRollups_createsNewWatermarkWhenMissing() {
        Instant maxCreatedAt = Instant.parse("2026-03-23T01:15:00Z");

        when(eventRollupWatermarkRepository.findById(20L)).thenReturn(Optional.empty());
        when(eventRollupSourceQueryRepository.findHourlyRollupBatchesAfter(20L, null))
                .thenReturn(List.of(
                        new EventHourlyRollupBatchProjection(
                                20L,
                                Instant.parse("2026-03-23T01:00:00Z"),
                                "/pricing",
                                "click",
                                3L,
                                2L,
                                maxCreatedAt
                        )
                ));

        EventRollupRefreshResult result = eventRollupService.refreshOrganizationHourlyRollups(20L);

        verify(eventHourlyRollupCommandRepository).upsertAll(List.of(
                new EventHourlyRollupBatchProjection(
                        20L,
                        Instant.parse("2026-03-23T01:00:00Z"),
                        "/pricing",
                        "click",
                        3L,
                        2L,
                        maxCreatedAt
                )
        ));
        verify(eventRollupWatermarkRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getOrganizationId().equals(20L)
                        && maxCreatedAt.equals(saved.getProcessedCreatedAt())
        ));
        assertThat(result).isEqualTo(new EventRollupRefreshResult(20L, 1, maxCreatedAt));
    }

    @Test
    void refreshOrganizationHourlyRollups_returnsNoOpWhenNoNewBatchesExist() {
        Instant processedCreatedAt = Instant.parse("2026-03-23T02:00:00Z");
        EventRollupWatermark watermark = new EventRollupWatermark(20L, processedCreatedAt);

        when(eventRollupWatermarkRepository.findById(20L)).thenReturn(Optional.of(watermark));
        when(eventRollupSourceQueryRepository.findHourlyRollupBatchesAfter(20L, processedCreatedAt))
                .thenReturn(List.of());

        EventRollupRefreshResult result = eventRollupService.refreshOrganizationHourlyRollups(20L);

        verify(eventHourlyRollupCommandRepository, never()).upsertAll(org.mockito.ArgumentMatchers.anyList());
        verify(eventRollupWatermarkRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(result).isEqualTo(new EventRollupRefreshResult(20L, 0, processedCreatedAt));
    }
}
