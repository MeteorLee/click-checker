package com.clickchecker.event.repository;

import com.clickchecker.event.repository.projection.ActivityOverviewWindowSummaryProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@RequiredArgsConstructor
@Repository
public class ActivityOverviewNativeQueryRepository {

    private final EntityManager entityManager;

    public ActivityOverviewWindowSummaryProjection summarizeOverviewWindow(
            Instant previousFrom,
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                  COUNT(*) FILTER (
                    WHERE e.occurred_at >= :from
                      AND e.occurred_at < :to
                  ) AS current_total_events,
                  COUNT(DISTINCT e.event_user_id) FILTER (
                    WHERE e.occurred_at >= :from
                      AND e.occurred_at < :to
                      AND e.event_user_id IS NOT NULL
                  ) AS current_unique_users,
                  COUNT(*) FILTER (
                    WHERE e.occurred_at >= :from
                      AND e.occurred_at < :to
                      AND e.event_user_id IS NOT NULL
                  ) AS current_identified_events,
                  COUNT(*) FILTER (
                    WHERE e.occurred_at >= :previousFrom
                      AND e.occurred_at < :from
                  ) AS previous_total_events
                FROM events e
                LEFT JOIN users u ON u.id = e.event_user_id
                WHERE e.organization_id = :organizationId
                  AND e.occurred_at >= :previousFrom
                  AND e.occurred_at < :to
                """);

        if (externalUserId != null) {
            sql.append("  AND u.external_user_id = :externalUserId\n");
        }
        if (eventType != null) {
            sql.append("  AND e.event_type = :eventType\n");
        }

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("previousFrom", Timestamp.from(previousFrom));
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        query.setParameter("organizationId", organizationId);

        if (externalUserId != null) {
            query.setParameter("externalUserId", externalUserId);
        }
        if (eventType != null) {
            query.setParameter("eventType", eventType);
        }

        Object[] row = (Object[]) query.getSingleResult();
        return new ActivityOverviewWindowSummaryProjection(
                toLong(row[0]),
                toLong(row[1]),
                toLong(row[2]),
                toLong(row[3])
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Unsupported summary value type: " + value);
    }
}
