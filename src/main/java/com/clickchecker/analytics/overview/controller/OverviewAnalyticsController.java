package com.clickchecker.analytics.overview.controller;

import com.clickchecker.analytics.overview.controller.response.OverviewResponse;
import com.clickchecker.analytics.overview.service.OverviewAnalyticsService;
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
@RequestMapping("/api/events")
public class OverviewAnalyticsController {

    private final OverviewAnalyticsService overviewAnalyticsService;

    @GetMapping("/aggregates/overview")
    public OverviewResponse aggregateOverview(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String eventType
    ) {
        validateTimeRange(from, to);

        return overviewAnalyticsService.getOverview(from, to, authOrgId, externalUserId, eventType);
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
    }
}
