package com.clickchecker.analytics.retention.controller;

import com.clickchecker.analytics.retention.controller.response.DailyRetentionResponse;
import com.clickchecker.analytics.retention.service.RetentionAnalyticsService;
import com.clickchecker.web.resolver.CurrentOrganizationId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/events/analytics/retention")
public class RetentionAnalyticsController {

    private final RetentionAnalyticsService retentionAnalyticsService;

    @GetMapping("/daily")
    public DailyRetentionResponse daily(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "UTC") String timezone
    ) {
        validateTimeRange(from, to);
        ZoneId zoneId = parseZoneId(timezone);

        return retentionAnalyticsService.getDailyRetention(
                from,
                to,
                zoneId,
                authOrgId,
                externalUserId
        );
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
    }

    private ZoneId parseZoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`timezone` is invalid.");
        }
    }
}
