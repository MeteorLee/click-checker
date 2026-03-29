package com.clickchecker.analytics.overview.service;

import com.clickchecker.analytics.activity.controller.response.ActivityOverviewResponse;
import com.clickchecker.analytics.activity.service.ActivityOverviewCacheService;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.service.OrganizationMemberAccessService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AdminOverviewAnalyticsService {

    static final ZoneId DASHBOARD_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final OrganizationMemberAccessService organizationMemberAccessService;
    private final ActivityOverviewCacheService activityOverviewCacheService;

    @Transactional(readOnly = true)
    public ActivityOverviewResponse getOverview(
            Long accountId,
            Long organizationId,
            LocalDate from,
            LocalDate to
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(accountId, organizationId, OrganizationRole.VIEWER);

        Instant fromInstant = from.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();
        Instant toInstant = to.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();

        return activityOverviewCacheService.getOverview(fromInstant, toInstant, organizationId, null, null);
    }
}
