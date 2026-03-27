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
import com.clickchecker.eventrollup.repository.EventHourlyRollupReadRepository;
import com.clickchecker.eventrollup.repository.EventRollupWatermarkRepository;
import com.clickchecker.route.service.RouteKeyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class TrendAnalyticsService {

    private final EventTrendNativeQueryRepository eventTrendNativeQueryRepository;
    private final EventHourlyRollupReadRepository eventHourlyRollupReadRepository;
    private final EventRollupWatermarkRepository eventRollupWatermarkRepository;
    private final RouteKeyResolver routeKeyResolver;
    private final CanonicalEventTypeResolver canonicalEventTypeResolver;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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

        return fillMissingTimeBuckets(
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
                zoneId
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<TimeBucketCountProjection> countUniqueUsersByTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of(timezone);

        return fillMissingTimeBuckets(
                eventTrendNativeQueryRepository.countDistinctEventUsersBucketedOccurredAtBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        bucket,
                        timezone
                ),
                from,
                to,
                bucket,
                zoneId
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
        Optional<Instant> processedCreatedAt = findProcessedCreatedAt(organizationId);
        if (canUseHourlyRollup(externalUserId, from, to, bucket, zoneId) && processedCreatedAt.isPresent()) {
            return buildRouteTimeBucketItems(
                    readRouteTimeBucketsWithHourlyRollup(
                            from,
                            to,
                            organizationId,
                            eventType,
                            bucket,
                            zoneId,
                            processedCreatedAt.orElseThrow()
                    ),
                    organizationId,
                    from,
                    to,
                    bucket,
                    zoneId
            );
        }

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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CanonicalEventTypeTimeBucketItem> countByCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of(timezone);
        Optional<Instant> processedCreatedAt = findProcessedCreatedAt(organizationId);
        if (canUseHourlyRollup(externalUserId, from, to, bucket, zoneId) && processedCreatedAt.isPresent()) {
            return buildCanonicalEventTypeTimeBucketItems(
                    readEventTypeTimeBucketsWithHourlyRollup(
                            from,
                            to,
                            organizationId,
                            bucket,
                            zoneId,
                            processedCreatedAt.orElseThrow()
                    ),
                    organizationId,
                    from,
                    to,
                    bucket,
                    zoneId
            );
        }

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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<RouteEventTypeTimeBucketItem> countByRouteKeyAndCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        ZoneId zoneId = ZoneId.of(timezone);
        Optional<Instant> processedCreatedAt = findProcessedCreatedAt(organizationId);
        if (canUseHourlyRollup(externalUserId, from, to, bucket, zoneId) && processedCreatedAt.isPresent()) {
            List<RawPathEventTypeTimeBucketCountProjection> rawPathEventTypeBucketCounts =
                    readRouteEventTypeTimeBucketsWithHourlyRollup(
                            from,
                            to,
                            organizationId,
                            bucket,
                            zoneId,
                            processedCreatedAt.orElseThrow()
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

    private List<TimeBucketCountProjection> readTimeBucketsWithHourlyRollup(
            Instant from,
            Instant to,
            Long organizationId,
            String eventType,
            TimeBucket bucket,
            ZoneId zoneId,
            Instant processedCreatedAt
    ) {
        RollupWindowSegments segments = RollupWindowSegments.of(from, to);
        Map<Instant, Long> countsByUtcHour = new HashMap<>();

        if (segments.hasFullHours()) {
            RollupWindowSegments.TimeRange fullHours = segments.fullHours();
            mergeCounts(
                    countsByUtcHour,
                    eventHourlyRollupReadRepository.countHourlyBetween(
                            fullHours.from(),
                            fullHours.to(),
                            organizationId,
                            eventType
                    )
            );
            mergeCounts(
                    countsByUtcHour,
                    eventTrendNativeQueryRepository.countUtcHourlyOccurredAtBetweenCreatedAfter(
                            fullHours.from(),
                            fullHours.to(),
                            organizationId,
                            eventType,
                            processedCreatedAt
                    )
            );
        }

        for (RollupWindowSegments.TimeRange rawSegment : segments.rawSegments()) {
            mergeCounts(
                    countsByUtcHour,
                    eventTrendNativeQueryRepository.countUtcHourlyOccurredAtBetween(
                            rawSegment.from(),
                            rawSegment.to(),
                            organizationId,
                            eventType
                    )
            );
        }

        return rebucketUtcHourlyCounts(countsByUtcHour, bucket, zoneId);
    }

    private List<RawPathTimeBucketCountProjection> readRouteTimeBucketsWithHourlyRollup(
            Instant from,
            Instant to,
            Long organizationId,
            String eventType,
            TimeBucket bucket,
            ZoneId zoneId,
            Instant processedCreatedAt
    ) {
        RollupWindowSegments segments = RollupWindowSegments.of(from, to);
        Map<RawPathUtcHourKey, Long> countsByPathUtcHour = new HashMap<>();

        if (segments.hasFullHours()) {
            RollupWindowSegments.TimeRange fullHours = segments.fullHours();
            mergePathCounts(
                    countsByPathUtcHour,
                    eventHourlyRollupReadRepository.countHourlyPathBetween(
                            fullHours.from(),
                            fullHours.to(),
                            organizationId,
                            eventType
                    )
            );
            mergePathCounts(
                    countsByPathUtcHour,
                    eventTrendNativeQueryRepository.countUtcHourlyPathOccurredAtBetweenCreatedAfter(
                            fullHours.from(),
                            fullHours.to(),
                            organizationId,
                            eventType,
                            processedCreatedAt
                    )
            );
        }

        for (RollupWindowSegments.TimeRange rawSegment : segments.rawSegments()) {
            mergePathCounts(
                    countsByPathUtcHour,
                    eventTrendNativeQueryRepository.countUtcHourlyPathOccurredAtBetween(
                            rawSegment.from(),
                            rawSegment.to(),
                            organizationId,
                            eventType
                    )
            );
        }

        return rebucketRawPathCounts(countsByPathUtcHour, bucket, zoneId);
    }

    private List<RawEventTypeTimeBucketCountProjection> readEventTypeTimeBucketsWithHourlyRollup(
            Instant from,
            Instant to,
            Long organizationId,
            TimeBucket bucket,
            ZoneId zoneId,
            Instant processedCreatedAt
    ) {
        RollupWindowSegments segments = RollupWindowSegments.of(from, to);
        Map<RawEventTypeUtcHourKey, Long> countsByEventTypeUtcHour = new HashMap<>();

        if (segments.hasFullHours()) {
            RollupWindowSegments.TimeRange fullHours = segments.fullHours();
            mergeEventTypeCounts(
                    countsByEventTypeUtcHour,
                    eventHourlyRollupReadRepository.countHourlyEventTypeBetween(
                            fullHours.from(),
                            fullHours.to(),
                            organizationId
                    )
            );
            mergeEventTypeCounts(
                    countsByEventTypeUtcHour,
                    eventTrendNativeQueryRepository.countUtcHourlyEventTypeOccurredAtBetweenCreatedAfter(
                            fullHours.from(),
                            fullHours.to(),
                            organizationId,
                            processedCreatedAt
                    )
            );
        }

        for (RollupWindowSegments.TimeRange rawSegment : segments.rawSegments()) {
            mergeEventTypeCounts(
                    countsByEventTypeUtcHour,
                    eventTrendNativeQueryRepository.countUtcHourlyEventTypeOccurredAtBetween(
                            rawSegment.from(),
                            rawSegment.to(),
                            organizationId
                    )
            );
        }

        return rebucketRawEventTypeCounts(countsByEventTypeUtcHour, bucket, zoneId);
    }

    private List<RawPathEventTypeTimeBucketCountProjection> readRouteEventTypeTimeBucketsWithHourlyRollup(
            Instant from,
            Instant to,
            Long organizationId,
            TimeBucket bucket,
            ZoneId zoneId,
            Instant processedCreatedAt
    ) {
        RollupWindowSegments segments = RollupWindowSegments.of(from, to);
        Map<RawPathEventTypeUtcHourKey, Long> countsByPathEventTypeUtcHour = new HashMap<>();

        if (segments.hasFullHours()) {
            RollupWindowSegments.TimeRange fullHours = segments.fullHours();
            mergePathEventTypeCounts(
                    countsByPathEventTypeUtcHour,
                    eventHourlyRollupReadRepository.countHourlyPathEventTypeBetween(
                            fullHours.from(),
                            fullHours.to(),
                            organizationId
                    )
            );
            mergePathEventTypeCounts(
                    countsByPathEventTypeUtcHour,
                    eventTrendNativeQueryRepository.countUtcHourlyPathEventTypeOccurredAtBetweenCreatedAfter(
                            fullHours.from(),
                            fullHours.to(),
                            organizationId,
                            processedCreatedAt
                    )
            );
        }

        for (RollupWindowSegments.TimeRange rawSegment : segments.rawSegments()) {
            mergePathEventTypeCounts(
                    countsByPathEventTypeUtcHour,
                    eventTrendNativeQueryRepository.countUtcHourlyPathEventTypeOccurredAtBetween(
                            rawSegment.from(),
                            rawSegment.to(),
                            organizationId
                    )
            );
        }

        return rebucketRawPathEventTypeCounts(countsByPathEventTypeUtcHour, bucket, zoneId);
    }

    private void mergeCounts(Map<Instant, Long> target, List<TimeBucketCountProjection> counts) {
        for (TimeBucketCountProjection count : counts) {
            target.merge(count.bucketStart(), count.count(), Long::sum);
        }
    }

    private void mergePathCounts(Map<RawPathUtcHourKey, Long> target, List<RawPathTimeBucketCountProjection> counts) {
        for (RawPathTimeBucketCountProjection count : counts) {
            target.merge(new RawPathUtcHourKey(count.path(), count.bucketStart()), count.count(), Long::sum);
        }
    }

    private void mergeEventTypeCounts(
            Map<RawEventTypeUtcHourKey, Long> target,
            List<RawEventTypeTimeBucketCountProjection> counts
    ) {
        for (RawEventTypeTimeBucketCountProjection count : counts) {
            target.merge(
                    new RawEventTypeUtcHourKey(count.rawEventType(), count.bucketStart()),
                    count.count(),
                    Long::sum
            );
        }
    }

    private void mergePathEventTypeCounts(
            Map<RawPathEventTypeUtcHourKey, Long> target,
            List<RawPathEventTypeTimeBucketCountProjection> counts
    ) {
        for (RawPathEventTypeTimeBucketCountProjection count : counts) {
            target.merge(
                    new RawPathEventTypeUtcHourKey(count.path(), count.rawEventType(), count.bucketStart()),
                    count.count(),
                    Long::sum
            );
        }
    }

    private List<TimeBucketCountProjection> rebucketUtcHourlyCounts(
            Map<Instant, Long> countsByUtcHour,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        return countsByUtcHour.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> bucket.floor(entry.getKey(), zoneId),
                        Collectors.summingLong(Map.Entry::getValue)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new TimeBucketCountProjection(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<RawPathTimeBucketCountProjection> rebucketRawPathCounts(
            Map<RawPathUtcHourKey, Long> countsByPathUtcHour,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        return countsByPathUtcHour.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> new RawPathBucketKey(
                                entry.getKey().path(),
                                bucket.floor(entry.getKey().bucketStart(), zoneId)
                        ),
                        Collectors.summingLong(Map.Entry::getValue)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(RawPathBucketKey::bucketStart)
                        .thenComparing(RawPathBucketKey::path)))
                .map(entry -> new RawPathTimeBucketCountProjection(
                        entry.getKey().path(),
                        entry.getKey().bucketStart(),
                        entry.getValue()
                ))
                .toList();
    }

    private List<RawEventTypeTimeBucketCountProjection> rebucketRawEventTypeCounts(
            Map<RawEventTypeUtcHourKey, Long> countsByEventTypeUtcHour,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        return countsByEventTypeUtcHour.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> new RawEventTypeBucketKey(
                                entry.getKey().rawEventType(),
                                bucket.floor(entry.getKey().bucketStart(), zoneId)
                        ),
                        Collectors.summingLong(Map.Entry::getValue)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(RawEventTypeBucketKey::bucketStart)
                        .thenComparing(RawEventTypeBucketKey::rawEventType)))
                .map(entry -> new RawEventTypeTimeBucketCountProjection(
                        entry.getKey().rawEventType(),
                        entry.getKey().bucketStart(),
                        entry.getValue()
                ))
                .toList();
    }

    private List<RawPathEventTypeTimeBucketCountProjection> rebucketRawPathEventTypeCounts(
            Map<RawPathEventTypeUtcHourKey, Long> countsByPathEventTypeUtcHour,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        return countsByPathEventTypeUtcHour.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> new RawPathEventTypeBucketKey(
                                entry.getKey().path(),
                                entry.getKey().rawEventType(),
                                bucket.floor(entry.getKey().bucketStart(), zoneId)
                        ),
                        Collectors.summingLong(Map.Entry::getValue)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(RawPathEventTypeBucketKey::bucketStart)
                        .thenComparing(RawPathEventTypeBucketKey::path)
                        .thenComparing(RawPathEventTypeBucketKey::rawEventType)))
                .map(entry -> new RawPathEventTypeTimeBucketCountProjection(
                        entry.getKey().path(),
                        entry.getKey().rawEventType(),
                        entry.getKey().bucketStart(),
                        entry.getValue()
                ))
                .toList();
    }

    private boolean canUseHourlyRollup(
            String externalUserId,
            Instant from,
            Instant to,
            TimeBucket bucket,
            ZoneId zoneId
    ) {
        if (externalUserId != null) {
            return false;
        }

        return bucketStarts(from, to, bucket, zoneId).stream()
                .allMatch(this::isUtcHourAligned);
    }

    private boolean isUtcHourAligned(Instant bucketStart) {
        return bucketStart.equals(bucketStart.truncatedTo(ChronoUnit.HOURS));
    }

    private Optional<Instant> findProcessedCreatedAt(Long organizationId) {
        return eventRollupWatermarkRepository.findById(organizationId)
                .map(com.clickchecker.eventrollup.entity.EventRollupWatermark::getProcessedCreatedAt);
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

    private record RawPathUtcHourKey(
            String path,
            Instant bucketStart
    ) {
    }

    private record RawPathBucketKey(
            String path,
            Instant bucketStart
    ) {
    }

    private record RawEventTypeUtcHourKey(
            String rawEventType,
            Instant bucketStart
    ) {
    }

    private record RawEventTypeBucketKey(
            String rawEventType,
            Instant bucketStart
    ) {
    }

    private record RawPathEventTypeUtcHourKey(
            String path,
            String rawEventType,
            Instant bucketStart
    ) {
    }

    private record RawPathEventTypeBucketKey(
            String path,
            String rawEventType,
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
