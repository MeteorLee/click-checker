package com.clickchecker.analytics.funnel.controller;

import com.clickchecker.analytics.funnel.controller.request.FunnelReportRequest;
import com.clickchecker.analytics.funnel.controller.response.FunnelReportResponse;
import com.clickchecker.analytics.funnel.service.FunnelAnalyticsService;
import com.clickchecker.web.resolver.CurrentOrganizationId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/events/analytics/funnels")
public class FunnelAnalyticsController {

    private final FunnelAnalyticsService funnelAnalyticsService;

    @PostMapping("/report")
    public FunnelReportResponse report(
            @CurrentOrganizationId Long authOrgId,
            @RequestBody @Valid FunnelReportRequest request
    ) {
        validateTimeRange(request.from(), request.to());

        return funnelAnalyticsService.report(
                request.from(),
                request.to(),
                authOrgId,
                request.externalUserId(),
                request.steps()
        );
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
    }
}
