package com.clickchecker.analytics.retention.controller;

import com.clickchecker.analytics.retention.controller.response.RetentionMatrixResponse;
import com.clickchecker.analytics.retention.service.AdminRetentionAnalyticsService;
import com.clickchecker.security.principal.AdminPrincipal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
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
public class AdminRetentionAnalyticsController {

    private static final long MAX_RANGE_DAYS = 90;

    private final AdminRetentionAnalyticsService adminRetentionAnalyticsService;

    @GetMapping("/retention")
    public RetentionMatrixResponse retention(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) List<Integer> days,
            @RequestParam(required = false) Integer minCohortUsers
    ) {
        validateTimeRange(from, to);
        return adminRetentionAnalyticsService.getRetentionMatrix(
                principal.accountId(),
                organizationId,
                from,
                to,
                normalizeDays(days),
                normalizeMinCohortUsers(minCohortUsers)
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

    private int normalizeMinCohortUsers(Integer minCohortUsers) {
        if (minCohortUsers == null) {
            return 1;
        }
        if (minCohortUsers < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`minCohortUsers` must be at least 1.");
        }
        return minCohortUsers;
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
