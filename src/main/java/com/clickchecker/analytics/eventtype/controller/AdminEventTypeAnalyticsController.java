package com.clickchecker.analytics.eventtype.controller;

import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeAggregateResponse;
import com.clickchecker.analytics.eventtype.service.AdminEventTypeAnalyticsService;
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
public class AdminEventTypeAnalyticsController {

    private static final int MIN_TOP = 1;
    private static final int MAX_TOP = 100;

    private final AdminEventTypeAnalyticsService adminEventTypeAnalyticsService;

    @GetMapping("/event-types")
    public CanonicalEventTypeAggregateResponse eventTypes(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "30") int top
    ) {
        validateTimeRange(from, to);
        validateTop(top);

        return new CanonicalEventTypeAggregateResponse(
                organizationId,
                null,
                from.atStartOfDay(AdminEventTypeAnalyticsService.DASHBOARD_ZONE_ID).toInstant(),
                to.atStartOfDay(AdminEventTypeAnalyticsService.DASHBOARD_ZONE_ID).toInstant(),
                top,
                adminEventTypeAnalyticsService.getEventTypes(
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
