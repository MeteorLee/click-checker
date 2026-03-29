package com.clickchecker.event.repository;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.event.repository.projection.DayOfWeekCountProjection;
import com.clickchecker.event.repository.projection.DayTypeHourlyCountProjection;
import com.clickchecker.event.repository.projection.DayTypeSummaryProjection;
import com.clickchecker.event.repository.projection.RawEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.event.repository.support.TimeBucketSql;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class EventTrendNativeQueryRepository {

    private final EntityManager entityManager;

    public List<TimeBucketCountProjection> countUtcHourlyOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String eventType
    ) {
        return countUtcHourlyOccurredAtBetweenCreatedAfter(from, to, organizationId, eventType, null);
    }

    public List<TimeBucketCountProjection> countUtcHourlyOccurredAtBetweenCreatedAfter(
            Instant from,
            Instant to,
            Long organizationId,
            String eventType,
            Instant createdAfter
    ) {
        String bucketExpression = bucketStartExpression(TimeBucket.HOUR, "UTC");
        StringBuilder sql = new StringBuilder("""
                SELECT %s AS bucket_start, COUNT(*) AS event_count
                FROM events e
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                """.formatted(bucketExpression));

        if (eventType != null) {
            sql.append("  AND e.event_type = :eventType\n");
        }
        if (createdAfter != null) {
            sql.append("  AND e.created_at > :createdAfter\n");
        }

        sql.append("""
                GROUP BY 1
                ORDER BY 1
                """);

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        query.setParameter("organizationId", organizationId);

        if (eventType != null) {
            query.setParameter("eventType", eventType);
        }
        if (createdAfter != null) {
            query.setParameter("createdAfter", Timestamp.from(createdAfter));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new TimeBucketCountProjection(
                        toEpochInstant(row[0]),
                        toLong(row[1])
                ))
                .toList();
    }

    public List<RawPathTimeBucketCountProjection> countUtcHourlyPathOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String eventType
    ) {
        return countUtcHourlyPathOccurredAtBetweenCreatedAfter(from, to, organizationId, eventType, null);
    }

    public List<RawPathTimeBucketCountProjection> countUtcHourlyPathOccurredAtBetweenCreatedAfter(
            Instant from,
            Instant to,
            Long organizationId,
            String eventType,
            Instant createdAfter
    ) {
        String bucketExpression = bucketStartExpression(TimeBucket.HOUR, "UTC");
        StringBuilder sql = new StringBuilder("""
                SELECT e.path, %s AS bucket_start, COUNT(*) AS event_count
                FROM events e
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                  AND e.path IS NOT NULL
                  AND e.path <> ''
                """.formatted(bucketExpression));

        if (eventType != null) {
            sql.append("  AND e.event_type = :eventType\n");
        }
        if (createdAfter != null) {
            sql.append("  AND e.created_at > :createdAfter\n");
        }

        sql.append("""
                GROUP BY e.path, 2
                ORDER BY 2, e.path
                """);

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        query.setParameter("organizationId", organizationId);
        if (eventType != null) {
            query.setParameter("eventType", eventType);
        }
        if (createdAfter != null) {
            query.setParameter("createdAfter", Timestamp.from(createdAfter));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new RawPathTimeBucketCountProjection(
                        (String) row[0],
                        toEpochInstant(row[1]),
                        toLong(row[2])
                ))
                .toList();
    }

    public List<RawEventTypeTimeBucketCountProjection> countUtcHourlyEventTypeOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId
    ) {
        return countUtcHourlyEventTypeOccurredAtBetweenCreatedAfter(from, to, organizationId, null);
    }

    public List<RawEventTypeTimeBucketCountProjection> countUtcHourlyEventTypeOccurredAtBetweenCreatedAfter(
            Instant from,
            Instant to,
            Long organizationId,
            Instant createdAfter
    ) {
        String bucketExpression = bucketStartExpression(TimeBucket.HOUR, "UTC");
        StringBuilder sql = new StringBuilder("""
                SELECT e.event_type, %s AS bucket_start, COUNT(*) AS event_count
                FROM events e
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                  AND e.event_type IS NOT NULL
                  AND e.event_type <> ''
                """.formatted(bucketExpression));

        if (createdAfter != null) {
            sql.append("  AND e.created_at > :createdAfter\n");
        }

        sql.append("""
                GROUP BY e.event_type, 2
                ORDER BY 2, e.event_type
                """);

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        query.setParameter("organizationId", organizationId);
        if (createdAfter != null) {
            query.setParameter("createdAfter", Timestamp.from(createdAfter));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new RawEventTypeTimeBucketCountProjection(
                        (String) row[0],
                        toEpochInstant(row[1]),
                        toLong(row[2])
                ))
                .toList();
    }

    public List<RawPathEventTypeTimeBucketCountProjection> countUtcHourlyPathEventTypeOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId
    ) {
        return countUtcHourlyPathEventTypeOccurredAtBetweenCreatedAfter(from, to, organizationId, null);
    }

    public List<RawPathEventTypeTimeBucketCountProjection> countUtcHourlyPathEventTypeOccurredAtBetweenCreatedAfter(
            Instant from,
            Instant to,
            Long organizationId,
            Instant createdAfter
    ) {
        String bucketExpression = bucketStartExpression(TimeBucket.HOUR, "UTC");
        StringBuilder sql = new StringBuilder("""
                SELECT e.path, e.event_type, %s AS bucket_start, COUNT(*) AS event_count
                FROM events e
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                  AND e.path IS NOT NULL
                  AND e.path <> ''
                  AND e.event_type IS NOT NULL
                  AND e.event_type <> ''
                """.formatted(bucketExpression));

        if (createdAfter != null) {
            sql.append("  AND e.created_at > :createdAfter\n");
        }

        sql.append("""
                GROUP BY e.path, e.event_type, 3
                ORDER BY 3, e.path, e.event_type
                """);

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        query.setParameter("organizationId", organizationId);
        if (createdAfter != null) {
            query.setParameter("createdAfter", Timestamp.from(createdAfter));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new RawPathEventTypeTimeBucketCountProjection(
                        (String) row[0],
                        (String) row[1],
                        toEpochInstant(row[2]),
                        toLong(row[3])
                ))
                .toList();
    }

    public List<TimeBucketCountProjection> countBucketedOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket,
            String timezone
    ) {
        String bucketExpression = bucketStartExpression(bucket, timezone);
        StringBuilder sql = new StringBuilder("""
                SELECT %s AS bucket_start, COUNT(*) AS event_count
                FROM events e
                LEFT JOIN users u ON u.id = e.event_user_id
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                """.formatted(bucketExpression));

        appendOptionalFilters(sql, externalUserId, eventType);
        sql.append("""
                GROUP BY 1
                ORDER BY 1
                """);

        Query query = createQuery(sql.toString(), from, to, organizationId, externalUserId, eventType, timezone);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new TimeBucketCountProjection(
                        toEpochInstant(row[0]),
                        toLong(row[1])
                ))
                .toList();
    }

    public List<TimeBucketCountProjection> countDistinctEventUsersBucketedOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        String bucketExpression = bucketStartExpression(bucket, timezone);
        StringBuilder sql = new StringBuilder("""
                SELECT %s AS bucket_start, COUNT(DISTINCT e.event_user_id) AS user_count
                FROM events e
                LEFT JOIN users u ON u.id = e.event_user_id
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                  AND e.event_user_id IS NOT NULL
                """.formatted(bucketExpression));

        appendExternalUserFilter(sql, externalUserId);
        sql.append("""
                GROUP BY 1
                ORDER BY 1
                """);

        Query query = createQuery(sql.toString(), from, to, organizationId, externalUserId, null, timezone);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new TimeBucketCountProjection(
                        toEpochInstant(row[0]),
                        toLong(row[1])
                ))
                .toList();
    }

    public List<RawPathTimeBucketCountProjection> countBucketedPathOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket,
            String timezone
    ) {
        String bucketExpression = bucketStartExpression(bucket, timezone);
        StringBuilder sql = new StringBuilder("""
                SELECT e.path, %s AS bucket_start, COUNT(*) AS event_count
                FROM events e
                LEFT JOIN users u ON u.id = e.event_user_id
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                  AND e.path IS NOT NULL
                  AND e.path <> ''
                """.formatted(bucketExpression));

        appendOptionalFilters(sql, externalUserId, eventType);
        sql.append("""
                GROUP BY e.path, 2
                ORDER BY 2, e.path
                """);

        Query query = createQuery(sql.toString(), from, to, organizationId, externalUserId, eventType, timezone);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new RawPathTimeBucketCountProjection(
                        (String) row[0],
                        toEpochInstant(row[1]),
                        toLong(row[2])
                ))
                .toList();
    }

    public List<RawEventTypeTimeBucketCountProjection> countBucketedEventTypeOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        String bucketExpression = bucketStartExpression(bucket, timezone);
        StringBuilder sql = new StringBuilder("""
                SELECT e.event_type, %s AS bucket_start, COUNT(*) AS event_count
                FROM events e
                LEFT JOIN users u ON u.id = e.event_user_id
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                  AND e.event_type IS NOT NULL
                  AND e.event_type <> ''
                """.formatted(bucketExpression));

        appendExternalUserFilter(sql, externalUserId);
        sql.append("""
                GROUP BY e.event_type, 2
                ORDER BY 2, e.event_type
                """);

        Query query = createQuery(sql.toString(), from, to, organizationId, externalUserId, null, timezone);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new RawEventTypeTimeBucketCountProjection(
                        (String) row[0],
                        toEpochInstant(row[1]),
                        toLong(row[2])
                ))
                .toList();
    }

    public List<RawPathEventTypeTimeBucketCountProjection> countBucketedPathEventTypeOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        String bucketExpression = bucketStartExpression(bucket, timezone);
        StringBuilder sql = new StringBuilder("""
                SELECT e.path, e.event_type, %s AS bucket_start, COUNT(*) AS event_count
                FROM events e
                LEFT JOIN users u ON u.id = e.event_user_id
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                  AND e.path IS NOT NULL
                  AND e.path <> ''
                  AND e.event_type IS NOT NULL
                  AND e.event_type <> ''
                """.formatted(bucketExpression));

        appendExternalUserFilter(sql, externalUserId);
        sql.append("""
                GROUP BY e.path, e.event_type, 3
                ORDER BY 3, e.path, e.event_type
                """);

        Query query = createQuery(sql.toString(), from, to, organizationId, externalUserId, null, timezone);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new RawPathEventTypeTimeBucketCountProjection(
                        (String) row[0],
                        (String) row[1],
                        toEpochInstant(row[2]),
                        toLong(row[3])
                ))
                .toList();
    }

    public List<DayTypeSummaryProjection> summarizeByDayTypeOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String timezone
    ) {
        String localizedOccurredAt = localizedOccurredAtExpression(timezone);
        String sql = """
                SELECT CASE
                           WHEN EXTRACT(DOW FROM %s) IN (0, 6) THEN 'WEEKEND'
                           ELSE 'WEEKDAY'
                       END AS day_type,
                       COUNT(*) AS event_count,
                       COUNT(DISTINCT e.event_user_id) AS unique_user_count
                FROM events e
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                GROUP BY 1
                ORDER BY 1
                """.formatted(localizedOccurredAt);

        Query query = createQuery(sql, from, to, organizationId, null, null, timezone);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new DayTypeSummaryProjection(
                        (String) row[0],
                        toLong(row[1]),
                        toLong(row[2])
                ))
                .toList();
    }

    public List<DayOfWeekCountProjection> summarizeByDayOfWeekOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String timezone
    ) {
        String localizedOccurredAt = localizedOccurredAtExpression(timezone);
        String sql = """
                SELECT CAST(EXTRACT(DOW FROM %s) AS INTEGER) AS day_of_week,
                       COUNT(*) AS event_count,
                       COUNT(DISTINCT e.event_user_id) AS unique_user_count
                FROM events e
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                GROUP BY 1
                ORDER BY 1
                """.formatted(localizedOccurredAt);

        Query query = createQuery(sql, from, to, organizationId, null, null, timezone);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new DayOfWeekCountProjection(
                        toInteger(row[0]),
                        toLong(row[1]),
                        toLong(row[2])
                ))
                .toList();
    }

    public List<DayTypeHourlyCountProjection> countByDayTypeAndHourOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String timezone
    ) {
        String localizedOccurredAt = localizedOccurredAtExpression(timezone);
        String sql = """
                SELECT CASE
                           WHEN EXTRACT(DOW FROM %s) IN (0, 6) THEN 'WEEKEND'
                           ELSE 'WEEKDAY'
                       END AS day_type,
                       CAST(EXTRACT(HOUR FROM %s) AS INTEGER) AS hour_of_day,
                       COUNT(*) AS event_count
                FROM events e
                WHERE e.occurred_at >= :from
                  AND e.occurred_at < :to
                  AND e.organization_id = :organizationId
                GROUP BY 1, 2
                ORDER BY 1, 2
                """.formatted(localizedOccurredAt, localizedOccurredAt);

        Query query = createQuery(sql, from, to, organizationId, null, null, timezone);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new DayTypeHourlyCountProjection(
                        (String) row[0],
                        toInteger(row[1]),
                        toLong(row[2])
                ))
                .toList();
    }

    private String bucketStartExpression(TimeBucket bucket, String timezone) {
        return TimeBucketSql.epochBucketStartExpression("e.occurred_at", bucket, timezone);
    }

    private String localizedOccurredAtExpression(String timezone) {
        return "e.occurred_at AT TIME ZONE '%s'".formatted(timezone.replace("'", "''"));
    }

    private void appendOptionalFilters(StringBuilder sql, String externalUserId, String eventType) {
        appendExternalUserFilter(sql, externalUserId);
        if (eventType != null) {
            sql.append("  AND e.event_type = :eventType\n");
        }
    }

    private void appendExternalUserFilter(StringBuilder sql, String externalUserId) {
        if (externalUserId != null) {
            sql.append("  AND u.external_user_id = :externalUserId\n");
        }
    }

    private Query createQuery(
            String sql,
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            String timezone
    ) {
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        query.setParameter("organizationId", organizationId);

        if (externalUserId != null) {
            query.setParameter("externalUserId", externalUserId);
        }
        if (eventType != null) {
            query.setParameter("eventType", eventType);
        }

        return query;
    }

    private Instant toEpochInstant(Object value) {
        if (value instanceof Number number) {
            long epochMillis = Math.round(number.doubleValue() * 1000.0d);
            return Instant.ofEpochMilli(epochMillis);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        throw new IllegalArgumentException("Unsupported bucket epoch type: " + value);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Unsupported count type: " + value);
    }

    private int toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Unsupported integer type: " + value);
    }
}
