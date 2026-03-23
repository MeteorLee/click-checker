package com.clickchecker.analytics.activity.service;

import com.clickchecker.analytics.activity.controller.response.ActivityOverviewResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityOverviewCacheService {

    public static final String OVERVIEW_BY_WINDOW_CACHE = "activityOverviewByWindow";

    private final ActivityAnalyticsService activityAnalyticsService;

    @Cacheable(cacheNames = OVERVIEW_BY_WINDOW_CACHE)
    public ActivityOverviewResponse getOverview(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        return activityAnalyticsService.getOverview(from, to, organizationId, externalUserId, eventType);
    }
}
