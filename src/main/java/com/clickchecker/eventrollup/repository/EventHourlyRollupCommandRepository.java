package com.clickchecker.eventrollup.repository;

import com.clickchecker.eventrollup.repository.projection.EventHourlyRollupBatchProjection;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class EventHourlyRollupCommandRepository {

    private static final String UPSERT_HOURLY_ROLLUP_SQL = """
            INSERT INTO event_hourly_rollups (
                organization_id,
                bucket_start,
                path,
                event_type,
                event_count,
                identified_event_count,
                created_at,
                updated_at
            )
            VALUES (
                :organizationId,
                :bucketStart,
                :path,
                :eventType,
                :eventCount,
                :identifiedEventCount,
                NOW(),
                NOW()
            )
            ON CONFLICT (organization_id, bucket_start, path, event_type)
            DO UPDATE SET
                event_count = event_hourly_rollups.event_count + EXCLUDED.event_count,
                identified_event_count = event_hourly_rollups.identified_event_count + EXCLUDED.identified_event_count,
                updated_at = NOW()
            """;

    private final EntityManager entityManager;

    public void upsert(EventHourlyRollupBatchProjection batch) {
        entityManager.createNativeQuery(UPSERT_HOURLY_ROLLUP_SQL)
                .setParameter("organizationId", batch.organizationId())
                .setParameter("bucketStart", batch.bucketStart())
                .setParameter("path", batch.path())
                .setParameter("eventType", batch.eventType())
                .setParameter("eventCount", batch.eventCount())
                .setParameter("identifiedEventCount", batch.identifiedEventCount())
                .executeUpdate();
    }

    public void upsertAll(List<EventHourlyRollupBatchProjection> batches) {
        for (EventHourlyRollupBatchProjection batch : batches) {
            upsert(batch);
        }
    }
}
