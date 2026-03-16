package com.clickchecker.event.controller;

import com.clickchecker.event.controller.response.CanonicalEventTypeAggregateResponse;
import com.clickchecker.event.controller.response.CanonicalEventTypeTimeBucketAggregateResponse;
import com.clickchecker.event.controller.response.CountResponse;
import com.clickchecker.event.controller.response.OverviewResponse;
import com.clickchecker.event.controller.response.PathAggregateResponse;
import com.clickchecker.event.controller.response.RawEventTypeAggregateResponse;
import com.clickchecker.event.controller.response.RouteAggregateItem;
import com.clickchecker.event.controller.response.RouteAggregateResponse;
import com.clickchecker.event.controller.response.RouteEventTypeAggregateResponse;
import com.clickchecker.event.controller.response.RouteTimeBucketAggregateResponse;
import com.clickchecker.event.controller.response.TimeBucketAggregateResponse;
import com.clickchecker.event.model.TimeBucket;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.event.service.EventQueryService;
import com.clickchecker.web.resolver.CurrentOrganizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/events")
public class EventQueryController {

    private static final int MIN_TOP = 1;
    private static final int MAX_TOP = 100;

    private final EventQueryService eventQueryService;

    // 개발용
    @GetMapping("/aggregates/count")
    public CountResponse count(@RequestParam String eventType) {
        Long count = eventQueryService.countByEventType(eventType);
        return new CountResponse(eventType, count);
    }

    @GetMapping("/aggregates/overview")
    public OverviewResponse aggregateOverview(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType
    ) {
        validateTimeRange(from, to);

        return eventQueryService.getOverview(from, to, authOrgId, externalUserId, eventType);
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
                eventQueryService.countRawEventTypeBetween(from, to, authOrgId, externalUserId, top)
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
                eventQueryService.countByCanonicalEventTypeBetween(from, to, authOrgId, externalUserId, top)
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

        List<PathCountProjection> pathCounts = eventQueryService.countByPathBetween(from, to, authOrgId, externalUserId, eventType, top);
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

        List<RouteAggregateItem> routeCounts = eventQueryService.countByRouteKeyBetween(from, to, authOrgId, externalUserId, eventType, top);
        return new RouteAggregateResponse(authOrgId, externalUserId, from, to, eventType, top, routeCounts);
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
                eventQueryService.countByRouteKeyAndCanonicalEventTypeBetween(from, to, authOrgId, externalUserId, top)
        );
    }

    @GetMapping("/aggregates/route-time-buckets")
    public RouteTimeBucketAggregateResponse aggregateRouteTimeBuckets(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam TimeBucket bucket
    ) {
        validateTimeRange(from, to);
        validateTimezone(timezone);

        return new RouteTimeBucketAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                eventType,
                timezone,
                bucket,
                eventQueryService.countByRouteKeyTimeBucketBetween(
                        from,
                        to,
                        authOrgId,
                        externalUserId,
                        eventType,
                        bucket,
                        timezone
                )
        );
    }

    @GetMapping("/aggregates/event-type-time-buckets")
    public CanonicalEventTypeTimeBucketAggregateResponse aggregateEventTypeTimeBuckets(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam TimeBucket bucket
    ) {
        validateTimeRange(from, to);
        validateTimezone(timezone);

        return new CanonicalEventTypeTimeBucketAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                timezone,
                bucket,
                eventQueryService.countByCanonicalEventTypeTimeBucketBetween(
                        from,
                        to,
                        authOrgId,
                        externalUserId,
                        bucket,
                        timezone
                )
        );
    }

    @GetMapping("/aggregates/time-buckets")
    public TimeBucketAggregateResponse aggregateTimeBuckets(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam TimeBucket bucket
    ) {
        validateTimeRange(from, to);
        validateTimezone(timezone);

        List<TimeBucketCountProjection> items = eventQueryService.countByTimeBucketBetween(
                from,
                to,
                authOrgId,
                externalUserId,
                eventType,
                bucket,
                timezone
        );

        return new TimeBucketAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                eventType,
                timezone,
                bucket,
                items
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

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`timezone` is invalid.");
        }
    }
}
