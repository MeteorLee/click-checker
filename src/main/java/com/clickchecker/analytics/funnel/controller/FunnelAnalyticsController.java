package com.clickchecker.analytics.funnel.controller;

import com.clickchecker.analytics.funnel.controller.request.FunnelReportRequest;
import com.clickchecker.analytics.funnel.controller.response.FunnelReportResponse;
import com.clickchecker.analytics.funnel.service.FunnelAnalyticsService;
import com.clickchecker.security.principal.ApiKeyPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @RequestBody @Valid FunnelReportRequest request
    ) {
        Long authOrgId = principal.organizationId();
        validateTimeRange(request.from(), request.to());

        return funnelAnalyticsService.report(
                request.from(),
                request.to(),
                authOrgId,
                request.externalUserId(),
                request.conversionWindowDays(),
                request.steps()
        );
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
    }
}
