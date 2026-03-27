package com.clickchecker.analytics.routes.controller;

import com.clickchecker.analytics.aggregate.controller.response.RouteAggregateResponse;
import com.clickchecker.analytics.routes.service.AdminRouteAnalyticsService;
import com.clickchecker.security.principal.AdminPrincipal;
import java.time.LocalDate;
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
public class AdminRouteAnalyticsController {

    private static final int MIN_TOP = 1;
    private static final int MAX_TOP = 100;

    private final AdminRouteAnalyticsService adminRouteAnalyticsService;

    @GetMapping("/routes")
    public RouteAggregateResponse routes(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "30") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        return new RouteAggregateResponse(
                organizationId,
                null,
                from.atStartOfDay(AdminRouteAnalyticsService.DASHBOARD_ZONE_ID).toInstant(),
                to.atStartOfDay(AdminRouteAnalyticsService.DASHBOARD_ZONE_ID).toInstant(),
                null,
                top,
                adminRouteAnalyticsService.getRoutes(
                        principal.accountId(),
                        organizationId,
                        from,
                        to,
                        top
                )
        );
    }

    private void validateTimeRange(LocalDate from, LocalDate to) {
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
}
