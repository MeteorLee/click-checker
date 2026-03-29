package com.clickchecker.analytics.overview.controller;

import com.clickchecker.analytics.activity.controller.response.ActivityOverviewResponse;
import com.clickchecker.analytics.overview.service.AdminOverviewAnalyticsService;
import com.clickchecker.security.principal.AdminPrincipal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/organizations/{organizationId}/analytics")
public class AdminOverviewAnalyticsController {

    private static final long MAX_RANGE_DAYS = 90;

    private final AdminOverviewAnalyticsService adminOverviewAnalyticsService;

    @GetMapping("/overview")
    public ActivityOverviewResponse overview(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        validateTimeRange(from, to);

        return adminOverviewAnalyticsService.getOverview(
                principal.accountId(),
                organizationId,
                from,
                to
        );
    }

    private void validateTimeRange(LocalDate from, LocalDate to) {
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
}
