package com.clickchecker.analytics.funnel.controller;

import com.clickchecker.analytics.funnel.controller.request.AdminFunnelReportRequest;
import com.clickchecker.analytics.funnel.controller.response.AdminFunnelOptionsResponse;
import com.clickchecker.analytics.funnel.controller.response.FunnelReportResponse;
import com.clickchecker.analytics.funnel.service.AdminFunnelAnalyticsService;
import com.clickchecker.security.principal.AdminPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/organizations/{organizationId}/analytics/funnels")
public class AdminFunnelAnalyticsController {

    private static final long MAX_RANGE_DAYS = 90;

    private final AdminFunnelAnalyticsService adminFunnelAnalyticsService;

    @GetMapping("/options")
    public AdminFunnelOptionsResponse options(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId
    ) {
        return adminFunnelAnalyticsService.getOptions(principal.accountId(), organizationId);
    }

    @PostMapping("/report")
    public FunnelReportResponse report(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestBody @Valid AdminFunnelReportRequest request
    ) {
        validateTimeRange(request.from(), request.to());
        return adminFunnelAnalyticsService.report(
                principal.accountId(),
                organizationId,
                request.from(),
                request.to(),
                request.conversionWindowDays(),
                request.steps()
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
