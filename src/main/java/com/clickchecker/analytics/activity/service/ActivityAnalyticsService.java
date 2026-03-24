package com.clickchecker.analytics.activity.service;

import com.clickchecker.analytics.activity.controller.response.ActivityOverviewResponse;
import com.clickchecker.event.repository.ActivityOverviewNativeQueryRepository;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.ActivityOverviewWindowSummaryProjection;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import com.clickchecker.route.service.RouteKeyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final ActivityOverviewNativeQueryRepository activityOverviewNativeQueryRepository;
    private final RouteKeyResolver routeKeyResolver;
    private final CanonicalEventTypeResolver canonicalEventTypeResolver;

    public ActivityOverviewResponse getOverview(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        Instant previousFrom = previousFrom(from, to);

        ActivityOverviewWindowSummaryProjection summary = activityOverviewNativeQueryRepository.summarizeOverviewWindow(
                previousFrom,
                from,
                to,
                organizationId,
                externalUserId,
                eventType
        );
        PathAnalysis pathAnalysis = analyzePaths(from, to, organizationId, externalUserId, eventType);
        EventTypeAnalysis eventTypeAnalysis = analyzeEventTypes(from, to, organizationId, externalUserId, eventType);

        return new ActivityOverviewResponse(
                organizationId,
                externalUserId,
                from,
                to,
                eventType,
                summary.currentTotalEvents(),
                summary.currentUniqueUsers(),
                identifiedEventRate(summary.currentTotalEvents(), summary.currentIdentifiedEvents()),
                eventTypeAnalysis.eventTypeMappingCoverage(),
                pathAnalysis.routeMatchCoverage(),
                toComparison(summary.currentTotalEvents(), summary.previousTotalEvents()),
                pathAnalysis.topRoutes(),
                eventTypeAnalysis.topEventTypes()
        );
    }

    public Double routeMatchCoverageBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        return analyzePaths(from, to, organizationId, externalUserId, eventType).routeMatchCoverage();
    }

    public Double eventTypeMappingCoverageBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        return analyzeEventTypes(from, to, organizationId, externalUserId, null).eventTypeMappingCoverage();
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

    private List<ActivityOverviewResponse.RouteSummary> toRouteSummaries(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        return analyzePaths(from, to, organizationId, externalUserId, eventType).topRoutes();
    }

    private PathAnalysis analyzePaths(
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

        if (rawPathCounts.isEmpty()) {
            return new PathAnalysis(null, List.of());
        }

        Map<String, String> routeKeysByRawPath = routeKeyResolver.resolveAll(
                organizationId,
                rawPathCounts.stream().map(PathCountProjection::path).toList()
        );
        long totalEventsWithPath = rawPathCounts.stream()
                .mapToLong(PathCountProjection::count)
                .sum();

        Map<String, Long> countsByRouteKey = rawPathCounts.stream()
                .collect(Collectors.groupingBy(
                        item -> routeKeysByRawPath.getOrDefault(item.path(), RouteKeyResolver.UNMATCHED_ROUTE),
                        Collectors.summingLong(PathCountProjection::count)
                ));
        long matchedEvents = countsByRouteKey.entrySet().stream()
                .filter(entry -> !RouteKeyResolver.UNMATCHED_ROUTE.equals(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();

        List<ActivityOverviewResponse.RouteSummary> topRoutes = countsByRouteKey.entrySet().stream()
                .map(entry -> new ActivityOverviewResponse.RouteSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(ActivityOverviewResponse.RouteSummary::count)
                        .reversed()
                        .thenComparing(ActivityOverviewResponse.RouteSummary::routeKey))
                .limit(OVERVIEW_SUMMARY_LIMIT)
                .toList();

        return new PathAnalysis(
                matchedEvents / (double) totalEventsWithPath,
                topRoutes
        );
    }

    private List<ActivityOverviewResponse.EventTypeSummary> toEventTypeSummaries(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        return analyzeEventTypes(from, to, organizationId, externalUserId, eventType).topEventTypes();
    }

    private EventTypeAnalysis analyzeEventTypes(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        if (eventType != null && !eventType.isBlank()) {
            List<ActivityOverviewResponse.EventTypeSummary> topEventTypes = eventQueryRepository.countByEventTypeBetween(
                            from,
                            to,
                            organizationId,
                            externalUserId,
                            eventType,
                            OVERVIEW_SUMMARY_LIMIT
                    ).stream()
                    .map(item -> new ActivityOverviewResponse.EventTypeSummary(item.eventType(), item.count()))
                    .toList();
            return new EventTypeAnalysis(null, topEventTypes);
        }

        List<RawEventTypeCountProjection> rawEventTypeCounts = eventQueryRepository.countRawEventTypeBetween(
                from,
                to,
                organizationId,
                externalUserId,
                Integer.MAX_VALUE
        );

        if (rawEventTypeCounts.isEmpty()) {
            return new EventTypeAnalysis(null, List.of());
        }

        Map<String, String> canonicalEventTypesByRawEventType = canonicalEventTypeResolver.resolveAll(
                organizationId,
                rawEventTypeCounts.stream().map(RawEventTypeCountProjection::rawEventType).toList()
        );
        long totalEventsWithEventType = rawEventTypeCounts.stream()
                .mapToLong(RawEventTypeCountProjection::count)
                .sum();
        long mappedEvents = rawEventTypeCounts.stream()
                .filter(item -> !CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE.equals(
                        canonicalEventTypesByRawEventType.getOrDefault(
                                item.rawEventType(),
                                CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                        )
                ))
                .mapToLong(RawEventTypeCountProjection::count)
                .sum();

        Map<String, Long> countsByCanonicalEventType = rawEventTypeCounts.stream()
                .collect(Collectors.groupingBy(
                        item -> canonicalEventTypesByRawEventType.getOrDefault(
                                item.rawEventType(),
                                CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                        ),
                        Collectors.summingLong(RawEventTypeCountProjection::count)
                ));

        List<ActivityOverviewResponse.EventTypeSummary> topEventTypes = countsByCanonicalEventType.entrySet().stream()
                .map(entry -> new ActivityOverviewResponse.EventTypeSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(ActivityOverviewResponse.EventTypeSummary::count)
                        .reversed()
                        .thenComparing(ActivityOverviewResponse.EventTypeSummary::eventType))
                .limit(OVERVIEW_SUMMARY_LIMIT)
                .toList();

        return new EventTypeAnalysis(
                mappedEvents / (double) totalEventsWithEventType,
                topEventTypes
        );
    }

    private record PathAnalysis(
            Double routeMatchCoverage,
            List<ActivityOverviewResponse.RouteSummary> topRoutes
    ) {
    }

    private record EventTypeAnalysis(
            Double eventTypeMappingCoverage,
            List<ActivityOverviewResponse.EventTypeSummary> topEventTypes
    ) {
    }
}
