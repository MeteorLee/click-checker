package com.clickchecker.event.repository;

import com.clickchecker.event.entity.QEvent;
import com.clickchecker.event.repository.dto.PathCountDto;
import com.clickchecker.event.repository.dto.TimeBucket;
import com.clickchecker.event.repository.dto.TimeBucketCountDto;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class EventQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Long countBetween(LocalDateTime from, LocalDateTime to) {

        QEvent event = QEvent.event;

        // from <= occurredAt < to
        Long result = queryFactory
                .select(event.id.count())
                .from(event)
                .where(occurredAtBetween(from, to))
                .fetchOne();

        return result != null ? result : 0L;
    }

    public List<PathCountDto> countByPathBetween(
            LocalDateTime from,
            LocalDateTime to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        QEvent event = QEvent.event;

        return queryFactory
                .select(Projections.constructor(PathCountDto.class, event.path, event.id.count()))
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

    public List<TimeBucketCountDto> countByTimeBucketBetween(
            LocalDateTime from,
            LocalDateTime to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket
    ) {
        QEvent event = QEvent.event;
        DateTimeExpression<LocalDateTime> bucketStart = timeBucketStartExpr(bucket);

        return queryFactory
                .select(Projections.constructor(TimeBucketCountDto.class, bucketStart, event.id.count()))
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

    private BooleanExpression occurredAtBetween(LocalDateTime from, LocalDateTime to) {
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

    private DateTimeExpression<LocalDateTime> timeBucketStartExpr(TimeBucket bucket) {
        QEvent event = QEvent.event;
        return switch (bucket) {
            case HOUR -> Expressions.dateTimeTemplate(
                    LocalDateTime.class,
                    "date_trunc('hour', {0})",
                    event.occurredAt
            );
            case DAY -> Expressions.dateTimeTemplate(
                    LocalDateTime.class,
                    "date_trunc('day', {0})",
                    event.occurredAt
            );
        };
    }
}
