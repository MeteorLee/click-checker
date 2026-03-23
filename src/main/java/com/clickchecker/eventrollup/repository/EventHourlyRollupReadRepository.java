package com.clickchecker.eventrollup.repository;

import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathTimeBucketCountProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class EventHourlyRollupReadRepository {

    private final EntityManager entityManager;

    public List<TimeBucketCountProjection> countHourlyBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String eventType
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.bucket_start, SUM(r.event_count) AS event_count
                FROM event_hourly_rollups r
                WHERE r.bucket_start >= :from
                  AND r.bucket_start < :to
                  AND r.organization_id = :organizationId
                """);

        if (eventType != null) {
            sql.append("  AND r.event_type = :eventType\n");
        }

        sql.append("""
                GROUP BY r.bucket_start
                ORDER BY r.bucket_start
                """);

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("from", Timestamp.from(from))
                .setParameter("to", Timestamp.from(to))
                .setParameter("organizationId", organizationId);

        if (eventType != null) {
            query.setParameter("eventType", eventType);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new TimeBucketCountProjection(
                        toInstant(row[0]),
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    public List<RawPathTimeBucketCountProjection> countHourlyPathBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String eventType
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.path, r.bucket_start, SUM(r.event_count) AS event_count
                FROM event_hourly_rollups r
                WHERE r.bucket_start >= :from
                  AND r.bucket_start < :to
                  AND r.organization_id = :organizationId
                  AND r.path IS NOT NULL
                  AND r.path <> ''
                """);

        if (eventType != null) {
            sql.append("  AND r.event_type = :eventType\n");
        }

        sql.append("""
                GROUP BY r.path, r.bucket_start
                ORDER BY r.bucket_start, r.path
                """);

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("from", Timestamp.from(from))
                .setParameter("to", Timestamp.from(to))
                .setParameter("organizationId", organizationId);

        if (eventType != null) {
            query.setParameter("eventType", eventType);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new RawPathTimeBucketCountProjection(
                        (String) row[0],
                        toInstant(row[1]),
                        ((Number) row[2]).longValue()
                ))
                .toList();
    }

    public List<RawEventTypeTimeBucketCountProjection> countHourlyEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId
    ) {
        Query query = entityManager.createNativeQuery("""
                SELECT r.event_type, r.bucket_start, SUM(r.event_count) AS event_count
                FROM event_hourly_rollups r
                WHERE r.bucket_start >= :from
                  AND r.bucket_start < :to
                  AND r.organization_id = :organizationId
                  AND r.event_type IS NOT NULL
                  AND r.event_type <> ''
                GROUP BY r.event_type, r.bucket_start
                ORDER BY r.bucket_start, r.event_type
                """)
                .setParameter("from", Timestamp.from(from))
                .setParameter("to", Timestamp.from(to))
                .setParameter("organizationId", organizationId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new RawEventTypeTimeBucketCountProjection(
                        (String) row[0],
                        toInstant(row[1]),
                        ((Number) row[2]).longValue()
                ))
                .toList();
    }

    public List<RawPathEventTypeTimeBucketCountProjection> countHourlyPathEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId
    ) {
        Query query = entityManager.createNativeQuery("""
                SELECT r.path, r.event_type, r.bucket_start, SUM(r.event_count) AS event_count
                FROM event_hourly_rollups r
                WHERE r.bucket_start >= :from
                  AND r.bucket_start < :to
                  AND r.organization_id = :organizationId
                  AND r.path IS NOT NULL
                  AND r.path <> ''
                  AND r.event_type IS NOT NULL
                  AND r.event_type <> ''
                GROUP BY r.path, r.event_type, r.bucket_start
                ORDER BY r.bucket_start, r.path, r.event_type
                """)
                .setParameter("from", Timestamp.from(from))
                .setParameter("to", Timestamp.from(to))
                .setParameter("organizationId", organizationId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new RawPathEventTypeTimeBucketCountProjection(
                        (String) row[0],
                        (String) row[1],
                        toInstant(row[2]),
                        ((Number) row[3]).longValue()
                ))
                .toList();
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
        throw new IllegalStateException("unsupported bucket timestamp type: " + value);
    }
}
