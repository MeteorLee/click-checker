package com.clickchecker.event.repository;

import com.clickchecker.event.entity.QEvent;
import com.clickchecker.event.repository.projection.EventTypeCountProjection;
import com.clickchecker.event.repository.projection.IdentifiedUserEventTypeOccurredAtProjection;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeUserCountProjection;
import com.clickchecker.event.repository.projection.IdentifiedUserFirstSeenProjection;
import com.clickchecker.event.repository.projection.IdentifiedUserEventCountProjection;
import com.clickchecker.event.repository.projection.IdentifiedUserOccurredAtProjection;
import com.clickchecker.event.repository.projection.RawOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathUserCountProjection;
import com.querydsl.core.types.Projections;
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

    public long countEventsWithEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        QEvent event = QEvent.event;

        Long result = queryFactory
                .select(event.id.count())
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeExists()
                )
                .fetchOne();

        return result != null ? result : 0L;
    }

    public long countEventsWithPathBetween(
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
                        pathExists()
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

    public List<RawPathEventTypeCountProjection> countRawPathEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        RawPathEventTypeCountProjection.class,
                        event.path,
                        event.eventType,
                        event.id.count()
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        pathExists(),
                        eventTypeExists()
                )
                .groupBy(event.path, event.eventType)
                .orderBy(event.id.count().desc(), event.path.asc(), event.eventType.asc())
                .fetch();
    }

    public List<RawOccurredAtCountProjection> countRawOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        RawOccurredAtCountProjection.class,
                        event.occurredAt,
                        event.id.count()
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeEq(eventType)
                )
                .groupBy(event.occurredAt)
                .orderBy(event.occurredAt.asc())
                .fetch();
    }

    public List<RawPathOccurredAtCountProjection> countRawPathOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        RawPathOccurredAtCountProjection.class,
                        event.path,
                        event.occurredAt,
                        event.id.count()
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeEq(eventType),
                        pathExists()
                )
                .groupBy(event.path, event.occurredAt)
                .orderBy(event.occurredAt.asc(), event.path.asc())
                .fetch();
    }

    public List<RawEventTypeOccurredAtCountProjection> countRawEventTypeOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        RawEventTypeOccurredAtCountProjection.class,
                        event.eventType,
                        event.occurredAt,
                        event.id.count()
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeExists()
                )
                .groupBy(event.eventType, event.occurredAt)
                .orderBy(event.occurredAt.asc(), event.eventType.asc())
                .fetch();
    }

    public List<RawPathEventTypeOccurredAtCountProjection> countRawPathEventTypeOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        RawPathEventTypeOccurredAtCountProjection.class,
                        event.path,
                        event.eventType,
                        event.occurredAt,
                        event.id.count()
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        pathExists(),
                        eventTypeExists()
                )
                .groupBy(event.path, event.eventType, event.occurredAt)
                .orderBy(event.occurredAt.asc(), event.path.asc(), event.eventType.asc())
                .fetch();
    }

    public List<RawPathUserCountProjection> countRawPathUserBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        RawPathUserCountProjection.class,
                        event.path,
                        event.eventUser.id,
                        event.id.count()
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeEq(eventType),
                        pathExists(),
                        event.eventUser.isNotNull()
                )
                .groupBy(event.path, event.eventUser.id)
                .orderBy(event.id.count().desc(), event.path.asc(), event.eventUser.id.asc())
                .fetch();
    }

    public List<RawEventTypeUserCountProjection> countRawEventTypeUserBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        RawEventTypeUserCountProjection.class,
                        event.eventType,
                        event.eventUser.id,
                        event.id.count()
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        eventTypeExists(),
                        event.eventUser.isNotNull()
                )
                .groupBy(event.eventType, event.eventUser.id)
                .orderBy(event.id.count().desc(), event.eventType.asc(), event.eventUser.id.asc())
                .fetch();
    }

    public List<IdentifiedUserEventCountProjection> countIdentifiedUserEventBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        IdentifiedUserEventCountProjection.class,
                        event.eventUser.id,
                        event.id.count()
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        event.eventUser.isNotNull()
                )
                .groupBy(event.eventUser.id)
                .orderBy(event.eventUser.id.asc())
                .fetch();
    }

    public List<IdentifiedUserFirstSeenProjection> findIdentifiedUserFirstSeen(
            Long organizationId,
            String externalUserId
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        IdentifiedUserFirstSeenProjection.class,
                        event.eventUser.id,
                        event.occurredAt.min()
                ))
                .from(event)
                .where(
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        event.eventUser.isNotNull()
                )
                .groupBy(event.eventUser.id)
                .orderBy(event.eventUser.id.asc())
                .fetch();
    }

    public List<IdentifiedUserEventTypeOccurredAtProjection> findIdentifiedUserEventTypeOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        IdentifiedUserEventTypeOccurredAtProjection.class,
                        event.eventUser.id,
                        event.eventType,
                        event.occurredAt
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        event.eventUser.isNotNull(),
                        eventTypeExists()
                )
                .orderBy(event.eventUser.id.asc(), event.occurredAt.asc(), event.eventType.asc())
                .fetch();
    }

    public List<IdentifiedUserOccurredAtProjection> findIdentifiedUserOccurredAtBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(
                        IdentifiedUserOccurredAtProjection.class,
                        event.eventUser.id,
                        event.occurredAt
                ))
                .from(event)
                .where(
                        occurredAtBetween(from, to),
                        organizationIdEq(organizationId),
                        externalUserIdEq(externalUserId),
                        event.eventUser.isNotNull()
                )
                .orderBy(event.eventUser.id.asc(), event.occurredAt.asc())
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

    private BooleanExpression eventTypeExists() {
        QEvent event = QEvent.event;
        return event.eventType.isNotNull().and(event.eventType.isNotEmpty());
    }
}
