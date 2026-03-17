package com.clickchecker.analytics.activity.controller;

import com.clickchecker.analytics.activity.controller.response.ActivityOverviewResponse;
import com.clickchecker.analytics.activity.service.ActivityAnalyticsService;
import com.clickchecker.web.resolver.CurrentOrganizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    private final ActivityAnalyticsService activityAnalyticsService;

    @GetMapping("/aggregates/overview")
    public ActivityOverviewResponse aggregateOverview(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType
    ) {
        validateTimeRange(from, to);

        return activityAnalyticsService.getOverview(from, to, authOrgId, externalUserId, eventType);
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
    }
}
