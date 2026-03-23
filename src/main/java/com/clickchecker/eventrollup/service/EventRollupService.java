package com.clickchecker.eventrollup.service;

import com.clickchecker.eventrollup.entity.EventRollupWatermark;
import com.clickchecker.eventrollup.repository.EventHourlyRollupCommandRepository;
import com.clickchecker.eventrollup.repository.EventRollupSourceQueryRepository;
import com.clickchecker.eventrollup.repository.EventRollupWatermarkRepository;
import com.clickchecker.eventrollup.repository.projection.EventHourlyRollupBatchProjection;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EventRollupService {

    private final EventRollupWatermarkRepository eventRollupWatermarkRepository;
    private final EventRollupSourceQueryRepository eventRollupSourceQueryRepository;
    private final EventHourlyRollupCommandRepository eventHourlyRollupCommandRepository;

    @Transactional
    public EventRollupRefreshResult refreshOrganizationHourlyRollups(Long organizationId) {
        EventRollupWatermark watermark = eventRollupWatermarkRepository.findById(organizationId)
                .orElse(null);
        Instant processedCreatedAt = watermark != null ? watermark.getProcessedCreatedAt() : null;

        List<EventHourlyRollupBatchProjection> batches =
                eventRollupSourceQueryRepository.findHourlyRollupBatchesAfter(organizationId, processedCreatedAt);

        if (batches.isEmpty()) {
            return new EventRollupRefreshResult(organizationId, 0, processedCreatedAt);
        }

        eventHourlyRollupCommandRepository.upsertAll(batches);

        Instant newProcessedCreatedAt = batches.stream()
                .map(EventHourlyRollupBatchProjection::maxCreatedAt)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("rollup batch maxCreatedAt missing"));

        EventRollupWatermark updatedWatermark = watermark != null
                ? watermark
                : new EventRollupWatermark(organizationId, null);
        updatedWatermark.updateProcessedCreatedAt(newProcessedCreatedAt);
        eventRollupWatermarkRepository.save(updatedWatermark);

        return new EventRollupRefreshResult(organizationId, batches.size(), newProcessedCreatedAt);
    }
}
