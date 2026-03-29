package com.clickchecker.analytics.routes.service;

import com.clickchecker.analytics.aggregate.controller.response.RouteAggregateItem;
import com.clickchecker.analytics.aggregate.service.AggregateAnalyticsService;
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
public class AdminRouteAnalyticsService {

    public static final ZoneId DASHBOARD_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final OrganizationMemberAccessService organizationMemberAccessService;
    private final AggregateAnalyticsService aggregateAnalyticsService;

    @Transactional(readOnly = true)
    public List<RouteAggregateItem> getRoutes(
            Long accountId,
            Long organizationId,
            LocalDate from,
            LocalDate to,
            int top
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.VIEWER
        );

        Instant fromInstant = from.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();
        Instant toInstant = to.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();

        return aggregateAnalyticsService.countByRouteKeyBetween(
                fromInstant,
                toInstant,
                organizationId,
                null,
                null,
                top
        );
    }
}
