package com.clickchecker.analytics.trend.controller;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.trend.controller.response.AdminTrendPointResponse;
import com.clickchecker.analytics.trend.controller.response.AdminTrendResponse;
import com.clickchecker.analytics.trend.service.AdminTrendAnalyticsService;
import com.clickchecker.security.principal.AdminPrincipal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
public class AdminTrendAnalyticsController {

    private static final long MAX_RANGE_DAYS = 90;
    private static final long MAX_HOURLY_RANGE_DAYS = 7;

    private final AdminTrendAnalyticsService adminTrendAnalyticsService;

    @GetMapping("/trends")
    public AdminTrendResponse trends(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam TimeBucket bucket
    ) {
        validateTimeRange(from, to);
        validateBucketRange(from, to, bucket);

        List<AdminTrendPointResponse> eventCounts = adminTrendAnalyticsService.getEventCounts(
                        principal.accountId(),
                        organizationId,
                        from,
                        to,
                        bucket
                ).stream()
                .map(item -> new AdminTrendPointResponse(item.bucketStart(), item.count()))
                .toList();

        List<AdminTrendPointResponse> uniqueUserCounts = adminTrendAnalyticsService.getUniqueUserCounts(
                        principal.accountId(),
                        organizationId,
                        from,
                        to,
                        bucket
                ).stream()
                .map(item -> new AdminTrendPointResponse(item.bucketStart(), item.count()))
                .toList();

        return new AdminTrendResponse(
                organizationId,
                from.atStartOfDay(AdminTrendAnalyticsService.DASHBOARD_ZONE_ID).toInstant(),
                to.atStartOfDay(AdminTrendAnalyticsService.DASHBOARD_ZONE_ID).toInstant(),
                AdminTrendAnalyticsService.DASHBOARD_ZONE_ID.getId(),
                bucket,
                eventCounts,
                uniqueUserCounts
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

    private void validateBucketRange(LocalDate from, LocalDate to, TimeBucket bucket) {
        if (bucket == TimeBucket.HOUR && ChronoUnit.DAYS.between(from, to) > MAX_HOURLY_RANGE_DAYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "`HOUR` bucket can span at most " + MAX_HOURLY_RANGE_DAYS + " days."
            );
        }
    }
}
