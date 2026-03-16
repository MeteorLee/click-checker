package com.clickchecker.event.service;

import com.clickchecker.event.controller.response.OverviewResponse;
import com.clickchecker.event.controller.response.CanonicalEventTypeItem;
import com.clickchecker.event.controller.response.CanonicalEventTypeTimeBucketItem;
import com.clickchecker.event.controller.response.CanonicalEventTypeUniqueUserItem;
import com.clickchecker.event.controller.response.RawEventTypeItem;
import com.clickchecker.event.controller.response.RouteAggregateItem;
import com.clickchecker.event.controller.response.RouteEventTypeAggregateItem;
import com.clickchecker.event.controller.response.RouteEventTypeTimeBucketItem;
import com.clickchecker.event.controller.response.RouteTimeBucketItem;
import com.clickchecker.event.controller.response.RouteUniqueUserItem;
import com.clickchecker.event.model.TimeBucket;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeUserCountProjection;
import com.clickchecker.event.repository.projection.RawOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathUserCountProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import com.clickchecker.route.service.RouteKeyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class EventQueryService {

    private static final int OVERVIEW_SUMMARY_LIMIT = 3;

    private final EventRepository eventRepository;
    private final EventQueryRepository eventQueryRepository;
    private final RouteKeyResolver routeKeyResolver;
    private final CanonicalEventTypeResolver canonicalEventTypeResolver;

    @Transactional(readOnly = true)
    public long countByEventType(String eventType) {
        return eventRepository.countByEventType(eventType);
    }

    @Transactional(readOnly = true)
    public OverviewResponse getOverview(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        long currentTotalEvents = eventQueryRepository.countBetween(from, to, organizationId, externalUserId, eventType);
        long currentUniqueUsers = eventQueryRepository.countUniqueUsersBetween(from, to, organizationId, externalUserId, eventType);
        long identifiedEvents = eventQueryRepository.countIdentifiedEventsBetween(from, to, organizationId, externalUserId, eventType);

        Instant previousFrom = previousFrom(from, to);
        long previousTotalEvents = eventQueryRepository.countBetween(previousFrom, from, organizationId, externalUserId, eventType);

        return new OverviewResponse(
                organizationId,
                externalUserId,
                from,
                to,
                eventType,
                currentTotalEvents,
                currentUniqueUsers,
                identifiedEventRate(currentTotalEvents, identifiedEvents),
                eventTypeMappingCoverage(from, to, organizationId, externalUserId, eventType),
                routeMatchCoverageBetween(from, to, organizationId, externalUserId, eventType),
                toComparison(currentTotalEvents, previousTotalEvents),
                toRouteSummaries(from, to, organizationId, externalUserId, eventType),
                toEventTypeSummaries(from, to, organizationId, externalUserId, eventType)
        );
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
    public Double eventTypeMappingCoverageBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        long totalEventsWithEventType = eventQueryRepository.countEventsWithEventTypeBetween(
                from,
                to,
                organizationId,
                externalUserId
        );

        if (totalEventsWithEventType == 0) {
            return null;
        }

        long mappedEvents = eventQueryRepository.countRawEventTypeBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        Integer.MAX_VALUE
                ).stream()
                .filter(item -> !CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE.equals(
                        canonicalEventTypeResolver.resolve(organizationId, item.rawEventType())
                ))
                .mapToLong(RawEventTypeCountProjection::count)
                .sum();

        return mappedEvents / (double) totalEventsWithEventType;
    }

    @Transactional(readOnly = true)
    public Double routeMatchCoverageBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        long totalEventsWithPath = eventQueryRepository.countEventsWithPathBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType
        );

        if (totalEventsWithPath == 0) {
            return null;
        }

        long matchedEvents = eventQueryRepository.countRawPathBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType
                ).stream()
                .filter(item -> !RouteKeyResolver.UNMATCHED_ROUTE.equals(
                        routeKeyResolver.resolve(organizationId, item.path())
                ))
                .mapToLong(PathCountProjection::count)
                .sum();

        return matchedEvents / (double) totalEventsWithPath;
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
    public List<TimeBucketCountProjection> countByTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of(timezone);
        return buildTimeBucketCounts(
                eventQueryRepository.countRawOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType
                ),
                from,
                to,
                bucket,
                zoneId
        );
    }

    @Transactional(readOnly = true)
    public List<RouteTimeBucketItem> countByRouteKeyTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket
    ) {
        return buildRouteTimeBucketItems(
                eventQueryRepository.countRawPathOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType
                ),
                organizationId,
                from,
                to,
                bucket,
                ZoneOffset.UTC
        );
    }

    @Transactional(readOnly = true)
    public List<RouteTimeBucketItem> countByRouteKeyTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of(timezone);
        return buildRouteTimeBucketItems(
                eventQueryRepository.countRawPathOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType
                ),
                organizationId,
                from,
                to,
                bucket,
                zoneId
        );
    }

    @Transactional(readOnly = true)
    public List<CanonicalEventTypeTimeBucketItem> countByCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket
    ) {
        return buildCanonicalEventTypeTimeBucketItems(
                eventQueryRepository.countRawEventTypeOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId
                ),
                organizationId,
                from,
                to,
                bucket,
                ZoneOffset.UTC
        );
    }

    @Transactional(readOnly = true)
    public List<CanonicalEventTypeTimeBucketItem> countByCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of(timezone);
        return buildCanonicalEventTypeTimeBucketItems(
                eventQueryRepository.countRawEventTypeOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId
                ),
                organizationId,
                from,
                to,
                bucket,
                zoneId
        );
    }

    @Transactional(readOnly = true)
    public List<RouteEventTypeTimeBucketItem> countByRouteKeyAndCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of(timezone);
        Map<RouteEventTypeTimeBucketKey, Long> countsByKey = eventQueryRepository.countRawPathEventTypeOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId
                ).stream()
                .collect(Collectors.groupingBy(
                        item -> new RouteEventTypeTimeBucketKey(
                                routeKeyResolver.resolve(organizationId, item.path()),
                                canonicalEventTypeResolver.resolve(organizationId, item.rawEventType()),
                                bucket.floor(item.occurredAt(), zoneId)
                        ),
                        Collectors.summingLong(RawPathEventTypeOccurredAtCountProjection::count)
                ));

        List<RouteEventTypeAxis> axes = countsByKey.keySet().stream()
                .map(key -> new RouteEventTypeAxis(key.routeKey(), key.canonicalEventType()))
                .distinct()
                .sorted(Comparator
                        .comparing(RouteEventTypeAxis::routeKey)
                        .thenComparing(RouteEventTypeAxis::canonicalEventType))
                .toList();

        if (axes.isEmpty()) {
            return List.of();
        }

        List<RouteEventTypeTimeBucketItem> items = new ArrayList<>();
        for (Instant bucketStart : bucketStarts(from, to, bucket, zoneId)) {
            for (RouteEventTypeAxis axis : axes) {
                items.add(new RouteEventTypeTimeBucketItem(
                        axis.routeKey(),
                        axis.canonicalEventType(),
                        bucketStart,
                        countsByKey.getOrDefault(
                                new RouteEventTypeTimeBucketKey(
                                        axis.routeKey(),
                                        axis.canonicalEventType(),
                                        bucketStart
                                ),
                                0L
                        )
                ));
            }
        }
        return items;
    }

    private List<TimeBucketCountProjection> buildTimeBucketCounts(
            List<RawOccurredAtCountProjection> counts,
            Instant from,
            Instant to,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        Map<Instant, Long> countsByBucket = counts.stream()
                .collect(Collectors.groupingBy(
                        item -> bucket.floor(item.occurredAt(), zoneId),
                        Collectors.summingLong(RawOccurredAtCountProjection::count)
                ));

        return bucketStarts(from, to, bucket, zoneId).stream()
                .map(bucketStart -> new TimeBucketCountProjection(
                        bucketStart,
                        countsByBucket.getOrDefault(bucketStart, 0L)
                ))
                .toList();
    }

    private List<RouteTimeBucketItem> buildRouteTimeBucketItems(
            List<RawPathOccurredAtCountProjection> counts,
            Long organizationId,
            Instant from,
            Instant to,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        Map<RouteTimeBucketKey, Long> countsByRouteBucket = counts.stream()
                .collect(Collectors.groupingBy(
                        item -> new RouteTimeBucketKey(
                                routeKeyResolver.resolve(organizationId, item.path()),
                                bucket.floor(item.occurredAt(), zoneId)
                        ),
                        Collectors.summingLong(RawPathOccurredAtCountProjection::count)
                ));

        List<String> routeKeys = countsByRouteBucket.keySet().stream()
                .map(RouteTimeBucketKey::routeKey)
                .distinct()
                .sorted()
                .toList();

        if (routeKeys.isEmpty()) {
            return List.of();
        }

        List<RouteTimeBucketItem> items = new ArrayList<>();
        for (Instant bucketStart : bucketStarts(from, to, bucket, zoneId)) {
            for (String routeKey : routeKeys) {
                items.add(new RouteTimeBucketItem(
                        routeKey,
                        bucketStart,
                        countsByRouteBucket.getOrDefault(new RouteTimeBucketKey(routeKey, bucketStart), 0L)
                ));
            }
        }
        return items;
    }

    private List<CanonicalEventTypeTimeBucketItem> buildCanonicalEventTypeTimeBucketItems(
            List<RawEventTypeOccurredAtCountProjection> counts,
            Long organizationId,
            Instant from,
            Instant to,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        Map<CanonicalEventTypeTimeBucketKey, Long> countsByEventTypeBucket = counts.stream()
                .collect(Collectors.groupingBy(
                        item -> new CanonicalEventTypeTimeBucketKey(
                                canonicalEventTypeResolver.resolve(organizationId, item.rawEventType()),
                                bucket.floor(item.occurredAt(), zoneId)
                        ),
                        Collectors.summingLong(RawEventTypeOccurredAtCountProjection::count)
                ));

        List<String> canonicalEventTypes = countsByEventTypeBucket.keySet().stream()
                .map(CanonicalEventTypeTimeBucketKey::canonicalEventType)
                .distinct()
                .sorted()
                .toList();

        if (canonicalEventTypes.isEmpty()) {
            return List.of();
        }

        List<CanonicalEventTypeTimeBucketItem> items = new ArrayList<>();
        for (Instant bucketStart : bucketStarts(from, to, bucket, zoneId)) {
            for (String canonicalEventType : canonicalEventTypes) {
                items.add(new CanonicalEventTypeTimeBucketItem(
                        canonicalEventType,
                        bucketStart,
                        countsByEventTypeBucket.getOrDefault(
                                new CanonicalEventTypeTimeBucketKey(canonicalEventType, bucketStart),
                                0L
                        )
                ));
            }
        }
        return items;
    }

    private Instant previousFrom(Instant from, Instant to) {
        return from.minus(Duration.between(from, to));
    }

    private OverviewResponse.Comparison toComparison(long current, long previous) {
        long delta = current - previous;
        Double deltaRate = previous == 0
                ? null
                : delta / (double) previous;

        return new OverviewResponse.Comparison(
                current,
                previous,
                delta,
                deltaRate,
                previous > 0
        );
    }

    private Double identifiedEventRate(long totalEvents, long identifiedEvents) {
        if (totalEvents == 0) {
            return null;
        }
        return identifiedEvents / (double) totalEvents;
    }

    private Double eventTypeMappingCoverage(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        if (eventType != null && !eventType.isBlank()) {
            return null;
        }

        return eventTypeMappingCoverageBetween(from, to, organizationId, externalUserId);
    }

    private List<OverviewResponse.RouteSummary> toRouteSummaries(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        return countByRouteKeyBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType,
                OVERVIEW_SUMMARY_LIMIT
        ).stream()
                .map(item -> new OverviewResponse.RouteSummary(item.routeKey(), item.count()))
                .toList();
    }

    private List<OverviewResponse.EventTypeSummary> toEventTypeSummaries(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        if (eventType == null || eventType.isBlank()) {
            return countByCanonicalEventTypeBetween(
                    from,
                    to,
                    organizationId,
                    externalUserId,
                    OVERVIEW_SUMMARY_LIMIT
            ).stream()
                    .map(item -> new OverviewResponse.EventTypeSummary(
                            item.canonicalEventType(),
                            item.count()
                    ))
                    .toList();
        }

        return eventQueryRepository.countByEventTypeBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType,
                        OVERVIEW_SUMMARY_LIMIT
                ).stream()
                .map(item -> new OverviewResponse.EventTypeSummary(item.eventType(), item.count()))
                .toList();
    }

    private record RouteEventTypeKey(
            String routeKey,
            String canonicalEventType
    ) {
    }

    private record RouteTimeBucketKey(
            String routeKey,
            Instant bucketStart
    ) {
    }

    private record CanonicalEventTypeTimeBucketKey(
            String canonicalEventType,
            Instant bucketStart
    ) {
    }

    private record RouteEventTypeTimeBucketKey(
            String routeKey,
            String canonicalEventType,
            Instant bucketStart
    ) {
    }

    private record RouteEventTypeAxis(
            String routeKey,
            String canonicalEventType
    ) {
    }

    private List<Instant> bucketStarts(
            Instant from,
            Instant to,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        List<Instant> bucketStarts = new ArrayList<>();
        Instant current = bucket.floor(from, zoneId);

        while (current.isBefore(to)) {
            bucketStarts.add(current);
            current = bucket.next(current, zoneId);
        }

        return bucketStarts;
    }
}
