package com.clickchecker.event.service;

import com.clickchecker.event.controller.response.OverviewResponse;
import com.clickchecker.event.controller.response.RouteAggregateItem;
import com.clickchecker.event.model.TimeBucket;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.event.repository.projection.EventTypeCountProjection;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
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
}
