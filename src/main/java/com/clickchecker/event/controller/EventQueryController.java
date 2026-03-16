package com.clickchecker.event.controller;

import com.clickchecker.event.controller.response.CountResponse;
import com.clickchecker.event.controller.response.OverviewResponse;
import com.clickchecker.event.controller.response.PathAggregateResponse;
import com.clickchecker.event.controller.response.RouteAggregateItem;
import com.clickchecker.event.controller.response.RouteAggregateResponse;
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
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/events")
public class EventQueryController {

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
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }

        return eventQueryService.getOverview(from, to, authOrgId, externalUserId, eventType);
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
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
        if (top < 1 || top > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`top` must be between 1 and 100.");
        }

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
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
        if (top < 1 || top > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`top` must be between 1 and 100.");
        }

        List<RouteAggregateItem> routeCounts = eventQueryService.countByRouteKeyBetween(from, to, authOrgId, externalUserId, eventType, top);
        return new RouteAggregateResponse(authOrgId, externalUserId, from, to, eventType, top, routeCounts);
    }

    @GetMapping("/aggregates/time-buckets")
    public TimeBucketAggregateResponse aggregateTimeBuckets(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam TimeBucket bucket
    ) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }

        List<TimeBucketCountProjection> items = eventQueryService.countByTimeBucketBetween(
                from,
                to,
                authOrgId,
                externalUserId,
                eventType,
                bucket
        );

        return new TimeBucketAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                eventType,
                bucket,
                items
        );
    }
}
