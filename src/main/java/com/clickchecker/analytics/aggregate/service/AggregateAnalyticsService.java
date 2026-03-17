package com.clickchecker.analytics.aggregate.service;

import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeItem;
import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeUniqueUserItem;
import com.clickchecker.analytics.aggregate.controller.response.RawEventTypeItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteAggregateItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteEventTypeAggregateItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteUniqueUserItem;
import com.clickchecker.analytics.aggregate.controller.response.UnmatchedPathItem;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeUserCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawPathUserCountProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import com.clickchecker.route.service.RouteKeyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AggregateAnalyticsService {

    private final EventRepository eventRepository;
    private final EventQueryRepository eventQueryRepository;
    private final RouteKeyResolver routeKeyResolver;
    private final CanonicalEventTypeResolver canonicalEventTypeResolver;

    @Transactional(readOnly = true)
    public long countByEventType(String eventType) {
        return eventRepository.countByEventType(eventType);
    }

    @Transactional(readOnly = true)
    public List<RawEventTypeItem> countRawEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            int top
    ) {
        return eventQueryRepository.countRawEventTypeBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        top
                ).stream()
                .map(item -> new RawEventTypeItem(item.rawEventType(), item.count()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CanonicalEventTypeItem> countByCanonicalEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            int top
    ) {
        Map<String, Long> countsByCanonicalEventType = eventQueryRepository.countRawEventTypeBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        Integer.MAX_VALUE
                ).stream()
                .collect(Collectors.groupingBy(
                        item -> canonicalEventTypeResolver.resolve(organizationId, item.rawEventType()),
                        Collectors.summingLong(RawEventTypeCountProjection::count)
                ));

        return countsByCanonicalEventType.entrySet().stream()
                .map(entry -> new CanonicalEventTypeItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(CanonicalEventTypeItem::count)
                        .reversed()
                        .thenComparing(CanonicalEventTypeItem::canonicalEventType))
                .limit(top)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PathCountProjection> countByPathBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        return eventQueryRepository.countByPathBetween(from, to, organizationId, externalUserId, eventType, top);
    }

    @Transactional(readOnly = true)
    public List<RouteAggregateItem> countByRouteKeyBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        Map<String, Long> countsByRouteKey = eventQueryRepository.countRawPathBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType
                ).stream()
                .collect(Collectors.groupingBy(
                        item -> routeKeyResolver.resolve(organizationId, item.path()),
                        Collectors.summingLong(PathCountProjection::count)
                ));

        return countsByRouteKey.entrySet().stream()
                .map(entry -> new RouteAggregateItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(RouteAggregateItem::count)
                        .reversed()
                        .thenComparing(RouteAggregateItem::routeKey))
                .limit(top)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UnmatchedPathItem> countUnmatchedPathsBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        return eventQueryRepository.countRawPathBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType
                ).stream()
                .filter(item -> RouteKeyResolver.UNMATCHED_ROUTE.equals(
                        routeKeyResolver.resolve(organizationId, item.path())
                ))
                .map(item -> new UnmatchedPathItem(item.path(), item.count()))
                .sorted(Comparator
                        .comparingLong(UnmatchedPathItem::count)
                        .reversed()
                        .thenComparing(UnmatchedPathItem::path))
                .limit(top)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RouteUniqueUserItem> countUniqueUsersByRouteKeyBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        Map<String, Long> uniqueUsersByRouteKey = eventQueryRepository.countRawPathUserBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType
                ).stream()
                .collect(Collectors.groupingBy(
                        item -> routeKeyResolver.resolve(organizationId, item.path()),
                        Collectors.mapping(
                                RawPathUserCountProjection::eventUserId,
                                Collectors.collectingAndThen(Collectors.toSet(), userIds -> (long) userIds.size())
                        )
                ));

        return uniqueUsersByRouteKey.entrySet().stream()
                .map(entry -> new RouteUniqueUserItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(RouteUniqueUserItem::uniqueUsers)
                        .reversed()
                        .thenComparing(RouteUniqueUserItem::routeKey))
                .limit(top)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CanonicalEventTypeUniqueUserItem> countUniqueUsersByCanonicalEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            int top
    ) {
        Map<String, Long> uniqueUsersByCanonicalEventType = eventQueryRepository.countRawEventTypeUserBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId
                ).stream()
                .collect(Collectors.groupingBy(
                        item -> canonicalEventTypeResolver.resolve(organizationId, item.rawEventType()),
                        Collectors.mapping(
                                RawEventTypeUserCountProjection::eventUserId,
                                Collectors.collectingAndThen(Collectors.toSet(), userIds -> (long) userIds.size())
                        )
                ));

        return uniqueUsersByCanonicalEventType.entrySet().stream()
                .map(entry -> new CanonicalEventTypeUniqueUserItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(CanonicalEventTypeUniqueUserItem::uniqueUsers)
                        .reversed()
                        .thenComparing(CanonicalEventTypeUniqueUserItem::canonicalEventType))
                .limit(top)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RouteEventTypeAggregateItem> countByRouteKeyAndCanonicalEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            int top
    ) {
        Map<RouteEventTypeKey, Long> countsByRouteEventType = eventQueryRepository.countRawPathEventTypeBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId
                ).stream()
                .collect(Collectors.groupingBy(
                        item -> new RouteEventTypeKey(
                                routeKeyResolver.resolve(organizationId, item.path()),
                                canonicalEventTypeResolver.resolve(organizationId, item.rawEventType())
                        ),
                        Collectors.summingLong(RawPathEventTypeCountProjection::count)
                ));

        return countsByRouteEventType.entrySet().stream()
                .map(entry -> new RouteEventTypeAggregateItem(
                        entry.getKey().routeKey(),
                        entry.getKey().canonicalEventType(),
                        entry.getValue()
                ))
                .sorted(Comparator
                        .comparingLong(RouteEventTypeAggregateItem::count)
                        .reversed()
                        .thenComparing(RouteEventTypeAggregateItem::routeKey)
                        .thenComparing(RouteEventTypeAggregateItem::canonicalEventType))
                .limit(top)
                .toList();
    }

    private record RouteEventTypeKey(
            String routeKey,
            String canonicalEventType
    ) {
    }
}
