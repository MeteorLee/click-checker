package com.clickchecker.analytics.activity.controller;

import com.clickchecker.analytics.activity.controller.response.ActivityOverviewResponse;
import com.clickchecker.analytics.activity.service.ActivityOverviewCacheService;
import com.clickchecker.security.principal.ApiKeyPrincipal;
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

    private final ActivityOverviewCacheService activityOverviewCacheService;

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

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
    }
}
