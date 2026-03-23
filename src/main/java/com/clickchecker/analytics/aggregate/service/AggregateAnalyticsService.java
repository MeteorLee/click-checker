package com.clickchecker.analytics.aggregate.service;

import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeItem;
import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeUniqueUserItem;
import com.clickchecker.analytics.aggregate.controller.response.RawEventTypeItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteAggregateItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteEventTypeAggregateItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteUniqueUserItem;
import com.clickchecker.analytics.aggregate.controller.response.UnmatchedPathItem;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeUserProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawPathUserProjection;
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

    private final EventQueryRepository eventQueryRepository;
    private final RouteKeyResolver routeKeyResolver;
    private final CanonicalEventTypeResolver canonicalEventTypeResolver;

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
        List<RawEventTypeCountProjection> rawEventTypeCounts = eventQueryRepository.countRawEventTypeBetween(
                from,
                to,
                organizationId,
                externalUserId,
                Integer.MAX_VALUE
        );
        Map<String, String> canonicalEventTypesByRawEventType = canonicalEventTypeResolver.resolveAll(
                organizationId,
                rawEventTypeCounts.stream().map(RawEventTypeCountProjection::rawEventType).toList()
        );

        Map<String, Long> countsByCanonicalEventType = rawEventTypeCounts.stream()
                .collect(Collectors.groupingBy(
                        item -> canonicalEventTypesByRawEventType.getOrDefault(
                                item.rawEventType(),
                                CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                        ),
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
        List<PathCountProjection> rawPathCounts = eventQueryRepository.countRawPathBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType
        );
        Map<String, String> routeKeysByRawPath = routeKeyResolver.resolveAll(
                organizationId,
                rawPathCounts.stream().map(PathCountProjection::path).toList()
        );

        Map<String, Long> countsByRouteKey = rawPathCounts.stream()
                .collect(Collectors.groupingBy(
                        item -> routeKeysByRawPath.getOrDefault(item.path(), RouteKeyResolver.UNMATCHED_ROUTE),
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
        List<PathCountProjection> rawPathCounts = eventQueryRepository.countRawPathBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType
        );
        Map<String, String> routeKeysByRawPath = routeKeyResolver.resolveAll(
                organizationId,
                rawPathCounts.stream().map(PathCountProjection::path).toList()
        );

        return rawPathCounts.stream()
                .filter(item -> RouteKeyResolver.UNMATCHED_ROUTE.equals(
                        routeKeysByRawPath.getOrDefault(item.path(), RouteKeyResolver.UNMATCHED_ROUTE)
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
        List<RawPathUserProjection> rawPathUserPairs = eventQueryRepository.findDistinctRawPathUserPairsBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType
        );
        Map<String, String> routeKeysByRawPath = routeKeyResolver.resolveAll(
                organizationId,
                rawPathUserPairs.stream().map(RawPathUserProjection::path).toList()
        );

        Map<String, Long> uniqueUsersByRouteKey = rawPathUserPairs.stream()
                .collect(Collectors.groupingBy(
                        item -> routeKeysByRawPath.getOrDefault(item.path(), RouteKeyResolver.UNMATCHED_ROUTE),
                        Collectors.mapping(
                                RawPathUserProjection::eventUserId,
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
        List<RawEventTypeUserProjection> rawEventTypeUserPairs =
                eventQueryRepository.findDistinctRawEventTypeUserPairsBetween(
                from,
                to,
                organizationId,
                externalUserId
        );
        Map<String, String> canonicalEventTypesByRawEventType = canonicalEventTypeResolver.resolveAll(
                organizationId,
                rawEventTypeUserPairs.stream().map(RawEventTypeUserProjection::rawEventType).toList()
        );

        Map<String, Long> uniqueUsersByCanonicalEventType = rawEventTypeUserPairs.stream()
                .collect(Collectors.groupingBy(
                        item -> canonicalEventTypesByRawEventType.getOrDefault(
                                item.rawEventType(),
                                CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                        ),
                        Collectors.mapping(
                                RawEventTypeUserProjection::eventUserId,
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
        List<RawPathEventTypeCountProjection> rawPathEventTypeCounts = eventQueryRepository.countRawPathEventTypeBetween(
                from,
                to,
                organizationId,
                externalUserId
        );
        Map<String, String> routeKeysByRawPath = routeKeyResolver.resolveAll(
                organizationId,
                rawPathEventTypeCounts.stream().map(RawPathEventTypeCountProjection::path).toList()
        );
        Map<String, String> canonicalEventTypesByRawEventType = canonicalEventTypeResolver.resolveAll(
                organizationId,
                rawPathEventTypeCounts.stream().map(RawPathEventTypeCountProjection::rawEventType).toList()
        );

        Map<RouteEventTypeKey, Long> countsByRouteEventType = rawPathEventTypeCounts.stream()
                .collect(Collectors.groupingBy(
                        item -> new RouteEventTypeKey(
                                routeKeysByRawPath.getOrDefault(item.path(), RouteKeyResolver.UNMATCHED_ROUTE),
                                canonicalEventTypesByRawEventType.getOrDefault(
                                        item.rawEventType(),
                                        CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                                )
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
