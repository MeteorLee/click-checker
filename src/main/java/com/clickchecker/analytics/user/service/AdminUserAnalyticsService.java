package com.clickchecker.analytics.user.service;

import com.clickchecker.analytics.user.controller.response.UserAnalyticsOverviewResponse;
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
public class AdminUserAnalyticsService {

    public static final ZoneId DASHBOARD_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final OrganizationMemberAccessService organizationMemberAccessService;
    private final UserAnalyticsService userAnalyticsService;

    @Transactional(readOnly = true)
    public UserAnalyticsOverviewResponse getOverview(
            Long accountId,
            Long organizationId,
            LocalDate from,
            LocalDate to
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.VIEWER
        );

        Instant fromInstant = from.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();
        Instant toInstant = to.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();

        return userAnalyticsService.getOverview(fromInstant, toInstant, organizationId, null);
    }
}
