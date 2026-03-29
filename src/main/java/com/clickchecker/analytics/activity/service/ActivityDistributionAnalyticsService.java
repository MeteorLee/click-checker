package com.clickchecker.analytics.activity.service;

import com.clickchecker.analytics.activity.controller.response.AdminActivityAnalyticsResponse;
import com.clickchecker.analytics.activity.controller.response.AdminActivityDayOfWeekItemResponse;
import com.clickchecker.analytics.activity.controller.response.AdminActivityDayTypeSummaryResponse;
import com.clickchecker.analytics.activity.controller.response.AdminActivityDailyItemResponse;
import com.clickchecker.analytics.activity.controller.response.AdminActivityHourlyItemResponse;
import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.trend.service.TrendAnalyticsService;
import com.clickchecker.event.repository.EventTrendNativeQueryRepository;
import com.clickchecker.event.repository.projection.DayOfWeekCountProjection;
import com.clickchecker.event.repository.projection.DayTypeHourlyCountProjection;
import com.clickchecker.event.repository.projection.DayTypeSummaryProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ActivityDistributionAnalyticsService {

    private final TrendAnalyticsService trendAnalyticsService;
    private final EventTrendNativeQueryRepository eventTrendNativeQueryRepository;

    @Transactional(readOnly = true)
    public AdminActivityAnalyticsResponse getActivity(
            Instant from,
            Instant to,
            Long organizationId,
            ZoneId zoneId
    ) {
        List<TimeBucketCountProjection> dailyEvents = trendAnalyticsService.countByTimeBucketBetween(
                from,
                to,
                organizationId,
                null,
                null,
                TimeBucket.DAY,
                zoneId.getId()
        );

        List<TimeBucketCountProjection> dailyUniqueUsers = trendAnalyticsService.countUniqueUsersByTimeBucketBetween(
                from,
                to,
                organizationId,
                null,
                TimeBucket.DAY,
                zoneId.getId()
        );

        List<TimeBucketCountProjection> hourlyEvents = trendAnalyticsService.countByTimeBucketBetween(
                from,
                to,
                organizationId,
                null,
                null,
                TimeBucket.HOUR,
                zoneId.getId()
        );
        List<DayTypeSummaryProjection> dayTypeSummaries = eventTrendNativeQueryRepository.summarizeByDayTypeOccurredAtBetween(
                from,
                to,
                organizationId,
                zoneId.getId()
        );
        List<DayOfWeekCountProjection> dayOfWeekCounts = eventTrendNativeQueryRepository.summarizeByDayOfWeekOccurredAtBetween(
                from,
                to,
                organizationId,
                zoneId.getId()
        );
        List<DayTypeHourlyCountProjection> dayTypeHourlyCounts = eventTrendNativeQueryRepository.countByDayTypeAndHourOccurredAtBetween(
                from,
                to,
                organizationId,
                zoneId.getId()
        );

        List<AdminActivityDailyItemResponse> dailyActivity = combineDailyActivity(dailyEvents, dailyUniqueUsers);
        List<AdminActivityHourlyItemResponse> hourlyDistribution = buildHourlyDistribution(hourlyEvents, zoneId);
        AdminActivityDayTypeSummaryResponse weekdaySummary = buildDayTypeSummary(
                dayTypeSummaries,
                "WEEKDAY",
                countMatchingDays(from, to, zoneId, false)
        );
        AdminActivityDayTypeSummaryResponse weekendSummary = buildDayTypeSummary(
                dayTypeSummaries,
                "WEEKEND",
                countMatchingDays(from, to, zoneId, true)
        );
        List<AdminActivityDayOfWeekItemResponse> dayOfWeekDistribution = buildDayOfWeekDistribution(dayOfWeekCounts);
        List<AdminActivityHourlyItemResponse> weekdayHourlyDistribution = buildDayTypeHourlyDistribution(dayTypeHourlyCounts, "WEEKDAY");
        List<AdminActivityHourlyItemResponse> weekendHourlyDistribution = buildDayTypeHourlyDistribution(dayTypeHourlyCounts, "WEEKEND");

        long totalEvents = dailyActivity.stream().mapToLong(AdminActivityDailyItemResponse::eventCount).sum();
        long activeDays = dailyActivity.stream().filter(item -> item.eventCount() > 0).count();
        int totalDays = dailyActivity.size();
        double averageEventsPerDay = totalDays == 0 ? 0 : (double) totalEvents / totalDays;

        AdminActivityDailyItemResponse peakDay = dailyActivity.stream()
                .max(Comparator.comparingLong(AdminActivityDailyItemResponse::eventCount))
                .orElse(new AdminActivityDailyItemResponse(from, 0, 0));

        return new AdminActivityAnalyticsResponse(
                organizationId,
                from,
                to,
                zoneId.getId(),
                totalEvents,
                averageEventsPerDay,
                activeDays,
                peakDay.bucketStart(),
                peakDay.eventCount(),
                weekdaySummary,
                weekendSummary,
                dayOfWeekDistribution,
                dailyActivity,
                hourlyDistribution,
                weekdayHourlyDistribution,
                weekendHourlyDistribution
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

    private List<AdminActivityHourlyItemResponse> buildHourlyDistribution(
            List<TimeBucketCountProjection> hourlyEvents,
            ZoneId zoneId
    ) {
        long[] counts = new long[24];
        for (TimeBucketCountProjection item : hourlyEvents) {
            int hourOfDay = item.bucketStart().atZone(zoneId).getHour();
            counts[hourOfDay] += item.count();
        }

        List<AdminActivityHourlyItemResponse> items = new ArrayList<>();
        for (int hour = 0; hour < counts.length; hour++) {
            items.add(new AdminActivityHourlyItemResponse(hour, counts[hour]));
        }
        return items;
    }

    private AdminActivityDayTypeSummaryResponse buildDayTypeSummary(
            List<DayTypeSummaryProjection> dayTypeSummaries,
            String dayType,
            int dayCount
    ) {
        DayTypeSummaryProjection summary = dayTypeSummaries.stream()
                .filter(item -> dayType.equals(item.dayType()))
                .findFirst()
                .orElse(new DayTypeSummaryProjection(dayType, 0, 0));

        double averageEventsPerDay = dayCount == 0 ? 0 : (double) summary.eventCount() / dayCount;
        double averageUniqueUsersPerDay = dayCount == 0 ? 0 : (double) summary.uniqueUserCount() / dayCount;
        return new AdminActivityDayTypeSummaryResponse(
                summary.eventCount(),
                summary.uniqueUserCount(),
                averageEventsPerDay,
                averageUniqueUsersPerDay
        );
    }

    private List<AdminActivityDayOfWeekItemResponse> buildDayOfWeekDistribution(
            List<DayOfWeekCountProjection> dayOfWeekCounts
    ) {
        Map<Integer, DayOfWeekCountProjection> countsByDayOfWeek = dayOfWeekCounts.stream()
                .collect(Collectors.toMap(
                        DayOfWeekCountProjection::dayOfWeek,
                        Function.identity()
                ));

        List<AdminActivityDayOfWeekItemResponse> items = new ArrayList<>();
        for (int dayOfWeek = 1; dayOfWeek <= 6; dayOfWeek++) {
            DayOfWeekCountProjection item = countsByDayOfWeek.get(dayOfWeek);
            items.add(new AdminActivityDayOfWeekItemResponse(
                    dayOfWeek,
                    item == null ? 0 : item.eventCount(),
                    item == null ? 0 : item.uniqueUserCount()
            ));
        }
        DayOfWeekCountProjection sunday = countsByDayOfWeek.get(0);
        items.add(new AdminActivityDayOfWeekItemResponse(
                0,
                sunday == null ? 0 : sunday.eventCount(),
                sunday == null ? 0 : sunday.uniqueUserCount()
        ));
        return items;
    }

    private List<AdminActivityHourlyItemResponse> buildDayTypeHourlyDistribution(
            List<DayTypeHourlyCountProjection> dayTypeHourlyCounts,
            String dayType
    ) {
        long[] counts = new long[24];
        for (DayTypeHourlyCountProjection item : dayTypeHourlyCounts) {
            if (dayType.equals(item.dayType())) {
                counts[item.hourOfDay()] = item.eventCount();
            }
        }

        List<AdminActivityHourlyItemResponse> items = new ArrayList<>();
        for (int hour = 0; hour < counts.length; hour++) {
            items.add(new AdminActivityHourlyItemResponse(hour, counts[hour]));
        }
        return items;
    }

    private int countMatchingDays(Instant from, Instant to, ZoneId zoneId, boolean weekend) {
        int count = 0;
        for (LocalDate current = from.atZone(zoneId).toLocalDate();
             current.isBefore(to.atZone(zoneId).toLocalDate());
             current = current.plusDays(1)) {
            int dayOfWeek = current.getDayOfWeek().getValue();
            boolean isWeekend = dayOfWeek == 6 || dayOfWeek == 7;
            if (weekend == isWeekend) {
                count++;
            }
        }
        return count;
    }
}
