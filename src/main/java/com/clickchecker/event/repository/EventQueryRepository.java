package com.clickchecker.event.repository;

import com.clickchecker.event.entity.QEvent;
import com.clickchecker.event.model.TimeBucket;
import com.clickchecker.event.repository.projection.EventTypeCountProjection;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class EventQueryRepository {

    private final JPAQueryFactory queryFactory;

    public long countBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        QEvent event = QEvent.event;

        Long result = queryFactory
                .select(event.id.count())
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeEq(eventType)
                )
                .fetchOne();

        return result != null ? result : 0L;
    }

    public long countUniqueUsersBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        QEvent event = QEvent.event;

        Long result = queryFactory
                .select(event.eventUser.id.countDistinct())
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeEq(eventType),
                        event.eventUser.isNotNull()
                )
                .fetchOne();

        return result != null ? result : 0L;
    }

    public long countIdentifiedEventsBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        QEvent event = QEvent.event;

        Long result = queryFactory
                .select(event.id.count())
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeEq(eventType),
                        event.eventUser.isNotNull()
                )
                .fetchOne();

        return result != null ? result : 0L;
    }

    public List<EventTypeCountProjection> countByEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(EventTypeCountProjection.class, event.eventType, event.id.count()))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeEq(eventType)
                )
                .groupBy(event.eventType)
                .orderBy(event.id.count().desc(), event.eventType.asc())
                .limit(top)
                .fetch();
    }

    public List<RawEventTypeCountProjection> countRawEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            int top
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(RawEventTypeCountProjection.class, event.eventType, event.id.count()))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId)
                )
                .groupBy(event.eventType)
                .orderBy(event.id.count().desc(), event.eventType.asc())
                .limit(top)
                .fetch();
    }

    public List<PathCountProjection> countByPathBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(PathCountProjection.class, event.path, event.id.count()))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        eventTypeEq(eventType),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        pathExists()
                )
                .groupBy(event.path)
                .orderBy(event.id.count().desc(), event.path.asc())
                .limit(top)
                .fetch();
    }

    public List<PathCountProjection> countRawPathBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(PathCountProjection.class, event.path, event.id.count()))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        eventTypeEq(eventType),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        pathExists()
                )
                .groupBy(event.path)
                .orderBy(event.id.count().desc(), event.path.asc())
                .fetch();
    }

    public List<TimeBucketCountProjection> countByTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket
    ) {
        QEvent event = QEvent.event;
        DateTimeExpression<Instant> bucketStart = timeBucketStartExpr(bucket);

        return queryFactory
                .select(Projections.constructor(TimeBucketCountProjection.class, bucketStart, event.id.count()))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        eventTypeEq(eventType),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId)
                )
                .groupBy(bucketStart)
                .orderBy(bucketStart.asc())
                .fetch();
    }

    private BooleanExpression occurredAtBetween(Instant from, Instant to) {
        QEvent event = QEvent.event;
        return event.occurredAt.goe(from)
                .and(event.occurredAt.lt(to));
    }

    private BooleanExpression eventTypeEq(String eventType) {
        QEvent event = QEvent.event;
        return eventType == null || eventType.isBlank() ? null : event.eventType.eq(eventType);
    }

    private BooleanExpression organizationIdEq(Long organizationId) {
        QEvent event = QEvent.event;
        return event.organization.id.eq(organizationId);
    }

    private BooleanExpression externalUserIdEq(String externalUserId) {
        QEvent event = QEvent.event;
        return externalUserId == null || externalUserId.isBlank() ? null : event.eventUser.externalUserId.eq(externalUserId);
    }

    private BooleanExpression pathExists() {
        QEvent event = QEvent.event;
        return event.path.isNotNull().and(event.path.isNotEmpty());
    }

    private DateTimeExpression<Instant> timeBucketStartExpr(TimeBucket bucket) {
        QEvent event = QEvent.event;
        return switch (bucket) {
            case HOUR -> Expressions.dateTimeTemplate(
                    Instant.class,
                    "date_trunc('hour', {0})",
                    event.occurredAt
            );
            case DAY -> Expressions.dateTimeTemplate(
                    Instant.class,
                    "date_trunc('day', {0})",
                    event.occurredAt
            );
        };
    }
}
