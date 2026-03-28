package com.clickchecker.analytics.activity.controller;

import com.clickchecker.analytics.activity.controller.response.AdminActivityAnalyticsResponse;
import com.clickchecker.analytics.activity.controller.response.ActivityOverviewResponse;
import com.clickchecker.analytics.activity.service.ActivityDistributionAnalyticsService;
import com.clickchecker.analytics.activity.service.ActivityOverviewCacheService;
import com.clickchecker.security.principal.ApiKeyPrincipal;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/events/analytics")
public class ActivityAnalyticsController {

    private static final long MAX_RANGE_DAYS = 90;

    private final ActivityOverviewCacheService activityOverviewCacheService;
    private final ActivityDistributionAnalyticsService activityDistributionAnalyticsService;

    @GetMapping("/aggregates/overview")
    public ActivityOverviewResponse aggregateOverview(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType
    ) {
        Long authOrgId = principal.organizationId();
        validateTimeRange(from, to);

        return activityOverviewCacheService.getOverview(from, to, authOrgId, externalUserId, eventType);
    }

    @GetMapping("/activity")
    public AdminActivityAnalyticsResponse activity(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "UTC") String timezone
    ) {
        Long authOrgId = principal.organizationId();
        validateTimeRange(from, to);
        ZoneId zoneId = parseZoneId(timezone);

        return activityDistributionAnalyticsService.getActivity(
                from,
                to,
                authOrgId,
                zoneId
        );
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }

        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "`from` and `to` must span at most " + MAX_RANGE_DAYS + " days."
            );
        }
    }

    private ZoneId parseZoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`timezone` is invalid.");
        }
    }
}
