package com.clickchecker.analytics.trend.controller;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.trend.controller.response.CanonicalEventTypeTimeBucketAggregateResponse;
import com.clickchecker.analytics.trend.controller.response.RouteEventTypeTimeBucketAggregateResponse;
import com.clickchecker.analytics.trend.controller.response.RouteTimeBucketAggregateResponse;
import com.clickchecker.analytics.trend.controller.response.TimeBucketAggregateResponse;
import com.clickchecker.security.principal.ApiKeyPrincipal;
import com.clickchecker.analytics.trend.service.TrendAnalyticsService;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/events/analytics")
public class TrendAnalyticsController {

    private static final int MAX_BUCKETS = 366;

    private final TrendAnalyticsService trendAnalyticsService;

    @GetMapping("/aggregates/route-event-type-time-buckets")
    public RouteEventTypeTimeBucketAggregateResponse aggregateRouteEventTypeTimeBuckets(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam TimeBucket bucket
    ) {
        Long authOrgId = principal.organizationId();
        validateTimeRange(from, to);
        ZoneId zoneId = validateTimezone(timezone);
        validateBucketRange(from, to, bucket, zoneId);

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
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam TimeBucket bucket
    ) {
        Long authOrgId = principal.organizationId();
        validateTimeRange(from, to);
        ZoneId zoneId = validateTimezone(timezone);
        validateBucketRange(from, to, bucket, zoneId);

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
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam TimeBucket bucket
    ) {
        Long authOrgId = principal.organizationId();
        validateTimeRange(from, to);
        ZoneId zoneId = validateTimezone(timezone);
        validateBucketRange(from, to, bucket, zoneId);

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
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam TimeBucket bucket
    ) {
        Long authOrgId = principal.organizationId();
        validateTimeRange(from, to);
        ZoneId zoneId = validateTimezone(timezone);
        validateBucketRange(from, to, bucket, zoneId);

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

    private void validateBucketRange(Instant from, Instant to, TimeBucket bucket, ZoneId zoneId) {
        int bucketCount = 0;
        Instant current = bucket.floor(from, zoneId);

        while (current.isBefore(to)) {
            bucketCount++;
            if (bucketCount > MAX_BUCKETS) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "`from` and `to` range is too large for the requested `bucket`."
                );
            }
            current = bucket.next(current, zoneId);
        }
    }
}
