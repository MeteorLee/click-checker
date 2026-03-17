package com.clickchecker.analytics.retention.controller;

import com.clickchecker.analytics.retention.controller.response.DailyRetentionResponse;
import com.clickchecker.analytics.retention.controller.response.RetentionMatrixResponse;
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
import java.util.Comparator;
import java.util.List;

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

    @GetMapping("/matrix")
    public RetentionMatrixResponse matrix(
            @CurrentOrganizationId Long authOrgId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam(required = false) List<Integer> days
    ) {
        validateTimeRange(from, to);
        ZoneId zoneId = parseZoneId(timezone);

        return retentionAnalyticsService.getRetentionMatrix(
                from,
                to,
                zoneId,
                authOrgId,
                externalUserId,
                normalizeDays(days)
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

    private List<Integer> normalizeDays(List<Integer> days) {
        List<Integer> requestedDays = (days == null || days.isEmpty())
                ? List.of(1, 7, 30)
                : days;

        if (requestedDays.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`days` must contain at most 10 entries.");
        }

        List<Integer> normalizedDays = requestedDays.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        boolean invalid = normalizedDays.stream().anyMatch(day -> day == null || day < 1 || day > 365);
        if (invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`days` must be between 1 and 365.");
        }

        return normalizedDays;
    }
}
