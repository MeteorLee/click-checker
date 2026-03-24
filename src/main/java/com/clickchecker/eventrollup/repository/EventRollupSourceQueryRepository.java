package com.clickchecker.eventrollup.repository;

import com.clickchecker.eventrollup.repository.projection.EventHourlyRollupBatchProjection;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class EventRollupSourceQueryRepository {

    private static final String UTC_HOURLY_BUCKET_SQL =
            "date_trunc('hour', timezone('UTC', e.occurred_at)) AT TIME ZONE 'UTC'";

    private static final String HOURLY_ROLLUP_BATCHES_SELECT_SQL = """
            SELECT
                e.organization_id,
                %s AS bucket_start,
                e.path,
                e.event_type,
                COUNT(*) AS event_count,
                COUNT(e.event_user_id) AS identified_event_count,
                MAX(e.created_at) AS max_created_at
            FROM events e
            """.formatted(UTC_HOURLY_BUCKET_SQL);

    private static final String HOURLY_ROLLUP_BATCHES_GROUP_AND_ORDER_SQL = """
            GROUP BY
                e.organization_id,
                %s,
                e.path,
                e.event_type
            ORDER BY bucket_start ASC, e.path ASC, e.event_type ASC
            """.formatted(UTC_HOURLY_BUCKET_SQL);

    private static final String FIND_HOURLY_ROLLUP_BATCHES_SQL = """
            %s
            WHERE e.organization_id = :organizationId
            %s
            """.formatted(HOURLY_ROLLUP_BATCHES_SELECT_SQL, HOURLY_ROLLUP_BATCHES_GROUP_AND_ORDER_SQL);

    private static final String FIND_HOURLY_ROLLUP_BATCHES_AFTER_SQL = """
            %s
            WHERE e.organization_id = :organizationId
              AND e.created_at > :processedCreatedAt
            %s
            """.formatted(HOURLY_ROLLUP_BATCHES_SELECT_SQL, HOURLY_ROLLUP_BATCHES_GROUP_AND_ORDER_SQL);

    private final EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<EventHourlyRollupBatchProjection> findHourlyRollupBatchesAfter(
            Long organizationId,
            Instant processedCreatedAt
    ) {
        List<Object[]> rows;
        if (processedCreatedAt == null) {
            rows = entityManager.createNativeQuery(FIND_HOURLY_ROLLUP_BATCHES_SQL)
                    .setParameter("organizationId", organizationId)
                    .getResultList();
        } else {
            rows = entityManager.createNativeQuery(FIND_HOURLY_ROLLUP_BATCHES_AFTER_SQL)
                    .setParameter("organizationId", organizationId)
                    .setParameter("processedCreatedAt", processedCreatedAt)
                    .getResultList();
        }

        return rows.stream()
                .map(this::toProjection)
                .toList();
    }

    private EventHourlyRollupBatchProjection toProjection(Object[] row) {
        return new EventHourlyRollupBatchProjection(
                ((Number) row[0]).longValue(),
                toInstant(row[1]),
                (String) row[2],
                (String) row[3],
                ((Number) row[4]).longValue(),
                ((Number) row[5]).longValue(),
                toInstant(row[6])
        );
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("unsupported timestamp type: " + value);
    }
}
