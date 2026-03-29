package com.clickchecker.analytics.trend.service;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
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
public class AdminTrendAnalyticsService {

    public static final ZoneId DASHBOARD_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final OrganizationMemberAccessService organizationMemberAccessService;
    private final TrendAnalyticsService trendAnalyticsService;

    @Transactional(readOnly = true)
    public List<TimeBucketCountProjection> getEventCounts(
            Long accountId,
            Long organizationId,
            LocalDate from,
            LocalDate to,
            TimeBucket bucket
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.VIEWER
        );

        Instant fromInstant = from.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();
        Instant toInstant = to.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();

        return trendAnalyticsService.countByTimeBucketBetween(
                fromInstant,
                toInstant,
                organizationId,
                null,
                null,
                bucket,
                DASHBOARD_ZONE_ID.getId()
        );
    }

    @Transactional(readOnly = true)
    public List<TimeBucketCountProjection> getUniqueUserCounts(
            Long accountId,
            Long organizationId,
            LocalDate from,
            LocalDate to,
            TimeBucket bucket
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.VIEWER
        );

        Instant fromInstant = from.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();
        Instant toInstant = to.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();

        return trendAnalyticsService.countUniqueUsersByTimeBucketBetween(
                fromInstant,
                toInstant,
                organizationId,
                null,
                bucket,
                DASHBOARD_ZONE_ID.getId()
        );
    }
}
