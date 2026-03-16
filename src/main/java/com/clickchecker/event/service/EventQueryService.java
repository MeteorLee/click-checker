package com.clickchecker.event.service;

import com.clickchecker.event.controller.response.OverviewResponse;
import com.clickchecker.event.controller.response.CanonicalEventTypeItem;
import com.clickchecker.event.controller.response.CanonicalEventTypeTimeBucketItem;
import com.clickchecker.event.controller.response.RawEventTypeItem;
import com.clickchecker.event.controller.response.RouteAggregateItem;
import com.clickchecker.event.controller.response.RouteEventTypeAggregateItem;
import com.clickchecker.event.controller.response.RouteTimeBucketItem;
import com.clickchecker.event.model.TimeBucket;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.event.repository.projection.EventTypeCountProjection;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import com.clickchecker.route.service.RouteKeyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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
        Instant previousTo = from;
        long previousTotalEvents = eventQueryRepository.countBetween(previousFrom, previousTo, organizationId, externalUserId, eventType);

        return new OverviewResponse(
                organizationId,
                externalUserId,
                from,
                to,
                eventType,
                currentTotalEvents,
                currentUniqueUsers,
                identifiedEventRate(currentTotalEvents, identifiedEvents),
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
                        Collectors.summingLong(item -> item.count())
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
    public List<TimeBucketCountProjection> countByTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket
    ) {
        return eventQueryRepository.countByTimeBucketBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType,
                bucket
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
        Map<RouteTimeBucketKey, Long> countsByRouteBucket = eventQueryRepository.countRawPathTimeBucketBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        eventType,
                        bucket
                ).stream()
                .collect(Collectors.groupingBy(
                        item -> new RouteTimeBucketKey(
                                routeKeyResolver.resolve(organizationId, item.path()),
                                item.bucketStart()
                        ),
                        Collectors.summingLong(item -> item.count())
                ));

        return countsByRouteBucket.entrySet().stream()
                .map(entry -> new RouteTimeBucketItem(
                        entry.getKey().routeKey(),
                        entry.getKey().bucketStart(),
                        entry.getValue()
                ))
                .sorted(Comparator
                        .comparing(RouteTimeBucketItem::bucketStart)
                        .thenComparing(RouteTimeBucketItem::routeKey))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CanonicalEventTypeTimeBucketItem> countByCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket
    ) {
        Map<CanonicalEventTypeTimeBucketKey, Long> countsByEventTypeBucket = eventQueryRepository.countRawEventTypeTimeBucketBetween(
                        from,
                        to,
                        organizationId,
                        externalUserId,
                        bucket
                ).stream()
                .collect(Collectors.groupingBy(
                        item -> new CanonicalEventTypeTimeBucketKey(
                                canonicalEventTypeResolver.resolve(organizationId, item.rawEventType()),
                                item.bucketStart()
                        ),
                        Collectors.summingLong(RawEventTypeTimeBucketCountProjection::count)
                ));

        return countsByEventTypeBucket.entrySet().stream()
                .map(entry -> new CanonicalEventTypeTimeBucketItem(
                        entry.getKey().canonicalEventType(),
                        entry.getKey().bucketStart(),
                        entry.getValue()
                ))
                .sorted(Comparator
                        .comparing(CanonicalEventTypeTimeBucketItem::bucketStart)
                        .thenComparing(CanonicalEventTypeTimeBucketItem::canonicalEventType))
                .toList();
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
}
