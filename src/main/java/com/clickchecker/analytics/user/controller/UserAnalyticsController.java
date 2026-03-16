package com.clickchecker.analytics.user.controller;

import com.clickchecker.analytics.user.controller.response.UserAnalyticsOverviewResponse;
import com.clickchecker.analytics.user.service.UserAnalyticsService;
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
@RequestMapping("/api/v1/events/analytics/users")
public class UserAnalyticsController {

    private final UserAnalyticsService userAnalyticsService;

    @GetMapping("/overview")
    public UserAnalyticsOverviewResponse overview(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        validateTimeRange(from, to);

        return userAnalyticsService.getOverview(from, to, authOrgId, externalUserId);
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
    }
}
