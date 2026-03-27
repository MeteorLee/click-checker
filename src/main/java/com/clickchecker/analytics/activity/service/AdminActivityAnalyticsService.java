package com.clickchecker.analytics.activity.service;

import com.clickchecker.analytics.activity.controller.response.AdminActivityAnalyticsResponse;
import com.clickchecker.analytics.activity.controller.response.AdminActivityDailyItemResponse;
import com.clickchecker.analytics.activity.controller.response.AdminActivityHourlyItemResponse;
import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.trend.service.TrendAnalyticsService;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.service.OrganizationMemberAccessService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AdminActivityAnalyticsService {

    public static final ZoneId DASHBOARD_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final OrganizationMemberAccessService organizationMemberAccessService;
    private final TrendAnalyticsService trendAnalyticsService;

    @Transactional(readOnly = true)
    public AdminActivityAnalyticsResponse getActivity(
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

        List<TimeBucketCountProjection> dailyEvents = trendAnalyticsService.countByTimeBucketBetween(
                fromInstant,
                toInstant,
                organizationId,
                null,
                null,
                TimeBucket.DAY,
                DASHBOARD_ZONE_ID.getId()
        );

        List<TimeBucketCountProjection> dailyUniqueUsers = trendAnalyticsService.countUniqueUsersByTimeBucketBetween(
                fromInstant,
                toInstant,
                organizationId,
                null,
                TimeBucket.DAY,
                DASHBOARD_ZONE_ID.getId()
        );

        List<TimeBucketCountProjection> hourlyEvents = trendAnalyticsService.countByTimeBucketBetween(
                fromInstant,
                toInstant,
                organizationId,
                null,
                null,
                TimeBucket.HOUR,
                DASHBOARD_ZONE_ID.getId()
        );

        List<AdminActivityDailyItemResponse> dailyActivity = combineDailyActivity(dailyEvents, dailyUniqueUsers);
        List<AdminActivityHourlyItemResponse> hourlyDistribution = buildHourlyDistribution(hourlyEvents);

        long totalEvents = dailyActivity.stream().mapToLong(AdminActivityDailyItemResponse::eventCount).sum();
        long activeDays = dailyActivity.stream().filter(item -> item.eventCount() > 0).count();
        int totalDays = dailyActivity.size();
        double averageEventsPerDay = totalDays == 0 ? 0 : (double) totalEvents / totalDays;

        AdminActivityDailyItemResponse peakDay = dailyActivity.stream()
                .max(Comparator.comparingLong(AdminActivityDailyItemResponse::eventCount))
                .orElse(new AdminActivityDailyItemResponse(fromInstant, 0, 0));

        return new AdminActivityAnalyticsResponse(
                organizationId,
                fromInstant,
                toInstant,
                DASHBOARD_ZONE_ID.getId(),
                totalEvents,
                averageEventsPerDay,
                activeDays,
                peakDay.bucketStart(),
                peakDay.eventCount(),
                dailyActivity,
                hourlyDistribution
        );
    }

    private List<AdminActivityDailyItemResponse> combineDailyActivity(
            List<TimeBucketCountProjection> dailyEvents,
            List<TimeBucketCountProjection> dailyUniqueUsers
    ) {
        List<AdminActivityDailyItemResponse> items = new ArrayList<>();
        for (int i = 0; i < dailyEvents.size(); i++) {
            TimeBucketCountProjection eventCount = dailyEvents.get(i);
            long uniqueUserCount = i < dailyUniqueUsers.size() ? dailyUniqueUsers.get(i).count() : 0;
            items.add(new AdminActivityDailyItemResponse(
                    eventCount.bucketStart(),
                    eventCount.count(),
                    uniqueUserCount
            ));
        }
        return items;
    }

    private List<AdminActivityHourlyItemResponse> buildHourlyDistribution(List<TimeBucketCountProjection> hourlyEvents) {
        long[] counts = new long[24];
        for (TimeBucketCountProjection item : hourlyEvents) {
            int hourOfDay = item.bucketStart().atZone(DASHBOARD_ZONE_ID).getHour();
            counts[hourOfDay] += item.count();
        }

        List<AdminActivityHourlyItemResponse> items = new ArrayList<>();
        for (int hour = 0; hour < counts.length; hour++) {
            items.add(new AdminActivityHourlyItemResponse(hour, counts[hour]));
        }
        return items;
    }
}
