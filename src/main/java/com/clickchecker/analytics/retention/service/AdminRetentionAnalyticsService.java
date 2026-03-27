package com.clickchecker.analytics.retention.service;

import com.clickchecker.analytics.retention.controller.response.RetentionMatrixResponse;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.service.OrganizationMemberAccessService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AdminRetentionAnalyticsService {

    public static final ZoneId DASHBOARD_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final OrganizationMemberAccessService organizationMemberAccessService;
    private final RetentionAnalyticsService retentionAnalyticsService;

    @Transactional(readOnly = true)
    public RetentionMatrixResponse getRetentionMatrix(
            Long accountId,
            Long organizationId,
            LocalDate from,
            LocalDate to,
            List<Integer> days,
            int minCohortUsers
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.VIEWER
        );

        Instant fromInstant = from.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();
        Instant toInstant = to.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();

        return retentionAnalyticsService.getRetentionMatrix(
                fromInstant,
                toInstant,
                DASHBOARD_ZONE_ID,
                organizationId,
                null,
                days,
                minCohortUsers
        );
    }
}
