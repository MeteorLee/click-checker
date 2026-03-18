package com.clickchecker.analytics.activity.service;

import com.clickchecker.analytics.activity.controller.response.ActivityOverviewResponse;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
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
public class ActivityAnalyticsService {

    private static final int OVERVIEW_SUMMARY_LIMIT = 3;

    private final EventQueryRepository eventQueryRepository;
    private final RouteKeyResolver routeKeyResolver;
    private final CanonicalEventTypeResolver canonicalEventTypeResolver;

    @Transactional(readOnly = true)
    public ActivityOverviewResponse getOverview(
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

        return new ActivityOverviewResponse(
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

        long matchedEvents = rawPathCounts.stream()
                .filter(item -> !RouteKeyResolver.UNMATCHED_ROUTE.equals(
                        routeKeysByRawPath.getOrDefault(item.path(), RouteKeyResolver.UNMATCHED_ROUTE)
                ))
                .mapToLong(PathCountProjection::count)
                .sum();

        return matchedEvents / (double) totalEventsWithPath;
    }

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

        long mappedEvents = rawEventTypeCounts.stream()
                .filter(item -> !CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE.equals(
                        canonicalEventTypesByRawEventType.getOrDefault(
                                item.rawEventType(),
                                CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                        )
                ))
                .mapToLong(RawEventTypeCountProjection::count)
                .sum();

        return mappedEvents / (double) totalEventsWithEventType;
    }

    private Instant previousFrom(Instant from, Instant to) {
        return from.minus(Duration.between(from, to));
    }

    private ActivityOverviewResponse.Comparison toComparison(long current, long previous) {
        long delta = current - previous;
        Double deltaRate = previous == 0
                ? null
                : delta / (double) previous;

        return new ActivityOverviewResponse.Comparison(
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

    private List<ActivityOverviewResponse.RouteSummary> toRouteSummaries(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
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
                .map(entry -> new ActivityOverviewResponse.RouteSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(ActivityOverviewResponse.RouteSummary::count)
                        .reversed()
                        .thenComparing(ActivityOverviewResponse.RouteSummary::routeKey))
                .limit(OVERVIEW_SUMMARY_LIMIT)
                .toList();
    }

    private List<ActivityOverviewResponse.EventTypeSummary> toEventTypeSummaries(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        if (eventType == null || eventType.isBlank()) {
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
                    .map(entry -> new ActivityOverviewResponse.EventTypeSummary(entry.getKey(), entry.getValue()))
                    .sorted(Comparator
                            .comparingLong(ActivityOverviewResponse.EventTypeSummary::count)
                            .reversed()
                            .thenComparing(ActivityOverviewResponse.EventTypeSummary::eventType))
                    .limit(OVERVIEW_SUMMARY_LIMIT)
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
                .map(item -> new ActivityOverviewResponse.EventTypeSummary(item.eventType(), item.count()))
                .toList();
    }
}
