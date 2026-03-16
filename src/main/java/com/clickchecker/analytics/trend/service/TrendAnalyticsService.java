package com.clickchecker.analytics.trend.service;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.trend.controller.response.CanonicalEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteTimeBucketItem;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.RawEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathOccurredAtCountProjection;
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

    private final EventQueryRepository eventQueryRepository;
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
