package com.clickchecker.analytics.trend.controller;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.trend.controller.response.CanonicalEventTypeTimeBucketAggregateResponse;
import com.clickchecker.analytics.trend.controller.response.RouteEventTypeTimeBucketAggregateResponse;
import com.clickchecker.analytics.trend.controller.response.RouteTimeBucketAggregateResponse;
import com.clickchecker.analytics.trend.controller.response.TimeBucketAggregateResponse;
import com.clickchecker.analytics.trend.service.TrendAnalyticsService;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.web.resolver.CurrentOrganizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/events")
public class TrendAnalyticsController {

    private final TrendAnalyticsService trendAnalyticsService;

    @GetMapping("/aggregates/route-event-type-time-buckets")
    public RouteEventTypeTimeBucketAggregateResponse aggregateRouteEventTypeTimeBuckets(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam TimeBucket bucket
    ) {
        validateTimeRange(from, to);
        ZoneId zoneId = validateTimezone(timezone);

        return new RouteEventTypeTimeBucketAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                zoneId.getId(),
                bucket,
                trendAnalyticsService.countByRouteKeyAndCanonicalEventTypeTimeBucketBetween(
                        from,
                        to,
                        authOrgId,
                        externalUserId,
                        bucket,
                        zoneId.getId()
                )
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
        ZoneId zoneId = validateTimezone(timezone);

        return new RouteTimeBucketAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                eventType,
                zoneId.getId(),
                bucket,
                trendAnalyticsService.countByRouteKeyTimeBucketBetween(
                        from,
                        to,
                        authOrgId,
                        externalUserId,
                        eventType,
                        bucket,
                        zoneId.getId()
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
        ZoneId zoneId = validateTimezone(timezone);

        return new CanonicalEventTypeTimeBucketAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                zoneId.getId(),
                bucket,
                trendAnalyticsService.countByCanonicalEventTypeTimeBucketBetween(
                        from,
                        to,
                        authOrgId,
                        externalUserId,
                        bucket,
                        zoneId.getId()
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
        ZoneId zoneId = validateTimezone(timezone);

        List<TimeBucketCountProjection> items = trendAnalyticsService.countByTimeBucketBetween(
                from,
                to,
                authOrgId,
                externalUserId,
                eventType,
                bucket,
                zoneId.getId()
        );

        return new TimeBucketAggregateResponse(
                authOrgId,
                externalUserId,
                from,
                to,
                eventType,
                zoneId.getId(),
                bucket,
                items
        );
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
    }

    private ZoneId validateTimezone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`timezone` is invalid.");
        }
    }
}
