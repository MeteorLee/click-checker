package com.clickchecker.analytics.aggregate.controller;

import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeAggregateResponse;
import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeUniqueUserAggregateResponse;
import com.clickchecker.analytics.aggregate.controller.response.CountResponse;
import com.clickchecker.analytics.aggregate.controller.response.PathAggregateResponse;
import com.clickchecker.analytics.aggregate.controller.response.RawEventTypeAggregateResponse;
import com.clickchecker.analytics.aggregate.controller.response.RouteAggregateResponse;
import com.clickchecker.analytics.aggregate.controller.response.RouteEventTypeAggregateResponse;
import com.clickchecker.analytics.aggregate.controller.response.RouteUniqueUserAggregateResponse;
import com.clickchecker.analytics.aggregate.controller.response.UnmatchedPathAggregateResponse;
import com.clickchecker.analytics.aggregate.service.AggregateAnalyticsService;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.web.resolver.CurrentOrganizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/events/analytics")
public class AggregateAnalyticsController {

    private static final int MIN_TOP = 1;
    private static final int MAX_TOP = 100;

    private final AggregateAnalyticsService aggregateAnalyticsService;

    @GetMapping("/aggregates/count")
    public CountResponse count(@RequestParam String eventType) {
        Long count = aggregateAnalyticsService.countByEventType(eventType);
        return new CountResponse(eventType, count);
    }

    @GetMapping("/aggregates/raw-event-types")
    public RawEventTypeAggregateResponse aggregateRawEventTypes(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "10") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        return new RawEventTypeAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                top,
                aggregateAnalyticsService.countRawEventTypeBetween(from, to, authOrgId, externalUserId, top)
        );
    }

    @GetMapping("/aggregates/event-types")
    public CanonicalEventTypeAggregateResponse aggregateCanonicalEventTypes(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "10") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        return new CanonicalEventTypeAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                top,
                aggregateAnalyticsService.countByCanonicalEventTypeBetween(from, to, authOrgId, externalUserId, top)
        );
    }

    @GetMapping("/aggregates/event-types/unique-users")
    public CanonicalEventTypeUniqueUserAggregateResponse aggregateCanonicalEventTypeUniqueUsers(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "10") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        return new CanonicalEventTypeUniqueUserAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                top,
                aggregateAnalyticsService.countUniqueUsersByCanonicalEventTypeBetween(
                        from,
                        to,
                        authOrgId,
                        externalUserId,
                        top
                )
        );
    }

    @GetMapping("/aggregates/paths")
    public PathAggregateResponse aggregatePaths(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "10") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        List<PathCountProjection> pathCounts = aggregateAnalyticsService.countByPathBetween(
                from,
                to,
                authOrgId,
                externalUserId,
                eventType,
                top
        );
        return new PathAggregateResponse(authOrgId, externalUserId, from, to, eventType, top, pathCounts);
    }

    @GetMapping("/aggregates/routes")
    public RouteAggregateResponse aggregateRoutes(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "10") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        return new RouteAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                eventType,
                top,
                aggregateAnalyticsService.countByRouteKeyBetween(
                        from,
                        to,
                        authOrgId,
                        externalUserId,
                        eventType,
                        top
                )
        );
    }

    @GetMapping("/aggregates/routes/unmatched-paths")
    public UnmatchedPathAggregateResponse aggregateUnmatchedPaths(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "10") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        return new UnmatchedPathAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                eventType,
                top,
                aggregateAnalyticsService.countUnmatchedPathsBetween(
                        from,
                        to,
                        authOrgId,
                        externalUserId,
                        eventType,
                        top
                )
        );
    }

    @GetMapping("/aggregates/routes/unique-users")
    public RouteUniqueUserAggregateResponse aggregateRouteUniqueUsers(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "10") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        return new RouteUniqueUserAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                eventType,
                top,
                aggregateAnalyticsService.countUniqueUsersByRouteKeyBetween(
                        from,
                        to,
                        authOrgId,
                        externalUserId,
                        eventType,
                        top
                )
        );
    }

    @GetMapping("/aggregates/route-event-types")
    public RouteEventTypeAggregateResponse aggregateRouteEventTypes(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "10") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        return new RouteEventTypeAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                top,
                aggregateAnalyticsService.countByRouteKeyAndCanonicalEventTypeBetween(from, to, authOrgId, externalUserId, top)
        );
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
    }

    private void validateTop(int top) {
        if (top < MIN_TOP || top > MAX_TOP) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "`top` must be between " + MIN_TOP + " and " + MAX_TOP + "."
            );
        }
    }
}
