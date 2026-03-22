package com.clickchecker.analytics.trend.service;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.trend.controller.response.CanonicalEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteTimeBucketItem;
import com.clickchecker.event.repository.EventTrendNativeQueryRepository;
import com.clickchecker.event.repository.projection.RawEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import com.clickchecker.route.service.RouteKeyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class TrendAnalyticsService {

    private final EventTrendNativeQueryRepository eventTrendNativeQueryRepository;
    private final RouteKeyResolver routeKeyResolver;
    private final CanonicalEventTypeResolver canonicalEventTypeResolver;

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
        List<TimeBucketCountProjection> result = fillMissingTimeBuckets(
                eventTrendNativeQueryRepository.countBucketedOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType,
                        bucket,
                        timezone
                ),
                from,
                to,
                bucket,
                ZoneId.of(timezone)
        );
        return result;
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
                eventTrendNativeQueryRepository.countBucketedPathOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType,
                        bucket,
                        timezone
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
            TimeBucket bucket,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of(timezone);
        return buildCanonicalEventTypeTimeBucketItems(
                eventTrendNativeQueryRepository.countBucketedEventTypeOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        bucket,
                        timezone
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
        List<RawPathEventTypeTimeBucketCountProjection> rawPathEventTypeBucketCounts =
                eventTrendNativeQueryRepository.countBucketedPathEventTypeOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        bucket,
                        timezone
                );
        Map<String, String> routeKeysByRawPath = routeKeyResolver.resolveAll(
                organizationId,
                rawPathEventTypeBucketCounts.stream().map(RawPathEventTypeTimeBucketCountProjection::path).toList()
        );
        Map<String, String> canonicalEventTypesByRawEventType = canonicalEventTypeResolver.resolveAll(
                organizationId,
                rawPathEventTypeBucketCounts.stream()
                        .map(RawPathEventTypeTimeBucketCountProjection::rawEventType)
                        .toList()
        );

        Map<RouteEventTypeTimeBucketKey, Long> countsByKey = rawPathEventTypeBucketCounts.stream()
                .collect(Collectors.groupingBy(
                        item -> new RouteEventTypeTimeBucketKey(
                                routeKeysByRawPath.getOrDefault(item.path(), RouteKeyResolver.UNMATCHED_ROUTE),
                                canonicalEventTypesByRawEventType.getOrDefault(
                                        item.rawEventType(),
                                        CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                                ),
                                item.bucketStart()
                        ),
                        Collectors.summingLong(RawPathEventTypeTimeBucketCountProjection::count)
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

    public List<RouteTimeBucketItem> countByRouteKeyTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket
    ) {
        return buildRouteTimeBucketItems(
                eventTrendNativeQueryRepository.countBucketedPathOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType,
                        bucket,
                        "UTC"
                ),
                organizationId,
                from,
                to,
                bucket,
                ZoneOffset.UTC
        );
    }

    public List<CanonicalEventTypeTimeBucketItem> countByCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket
    ) {
        return buildCanonicalEventTypeTimeBucketItems(
                eventTrendNativeQueryRepository.countBucketedEventTypeOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        bucket,
                        "UTC"
                ),
                organizationId,
                from,
                to,
                bucket,
                ZoneOffset.UTC
        );
    }

    private List<TimeBucketCountProjection> fillMissingTimeBuckets(
            List<TimeBucketCountProjection> counts,
            Instant from,
            Instant to,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        Map<Instant, Long> countsByBucket = counts.stream()
                .collect(Collectors.groupingBy(
                        TimeBucketCountProjection::bucketStart,
                        Collectors.summingLong(TimeBucketCountProjection::count)
                ));

        return bucketStarts(from, to, bucket, zoneId).stream()
                .map(bucketStart -> new TimeBucketCountProjection(
                        bucketStart,
                        countsByBucket.getOrDefault(bucketStart, 0L)
                ))
                .toList();
    }

    private List<RouteTimeBucketItem> buildRouteTimeBucketItems(
            List<RawPathTimeBucketCountProjection> counts,
            Long organizationId,
            Instant from,
            Instant to,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        Map<String, String> routeKeysByRawPath = routeKeyResolver.resolveAll(
                organizationId,
                counts.stream().map(RawPathTimeBucketCountProjection::path).toList()
        );
        Map<RouteTimeBucketKey, Long> countsByRouteBucket = counts.stream()
                .collect(Collectors.groupingBy(
                        item -> new RouteTimeBucketKey(
                                routeKeysByRawPath.getOrDefault(item.path(), RouteKeyResolver.UNMATCHED_ROUTE),
                                item.bucketStart()
                        ),
                        Collectors.summingLong(RawPathTimeBucketCountProjection::count)
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
            List<RawEventTypeTimeBucketCountProjection> counts,
            Long organizationId,
            Instant from,
            Instant to,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        Map<String, String> canonicalEventTypesByRawEventType = canonicalEventTypeResolver.resolveAll(
                organizationId,
                counts.stream().map(RawEventTypeTimeBucketCountProjection::rawEventType).toList()
        );
        Map<CanonicalEventTypeTimeBucketKey, Long> countsByEventTypeBucket = counts.stream()
                .collect(Collectors.groupingBy(
                        item -> new CanonicalEventTypeTimeBucketKey(
                                canonicalEventTypesByRawEventType.getOrDefault(
                                        item.rawEventType(),
                                        CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                                ),
                                item.bucketStart()
                        ),
                        Collectors.summingLong(RawEventTypeTimeBucketCountProjection::count)
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
}
