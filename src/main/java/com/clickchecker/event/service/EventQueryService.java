package com.clickchecker.event.service;

import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeItem;
import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeUniqueUserItem;
import com.clickchecker.analytics.aggregate.controller.response.RawEventTypeItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteAggregateItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteEventTypeAggregateItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteUniqueUserItem;
import com.clickchecker.analytics.aggregate.controller.response.UnmatchedPathItem;
import com.clickchecker.analytics.aggregate.service.AggregateAnalyticsService;
import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.overview.controller.response.OverviewResponse;
import com.clickchecker.analytics.overview.service.OverviewAnalyticsService;
import com.clickchecker.analytics.trend.controller.response.CanonicalEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteTimeBucketItem;
import com.clickchecker.analytics.trend.service.TrendAnalyticsService;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import com.clickchecker.route.service.RouteKeyResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class EventQueryService {

    private final OverviewAnalyticsService overviewAnalyticsService;
    private final AggregateAnalyticsService aggregateAnalyticsService;
    private final TrendAnalyticsService trendAnalyticsService;

    public EventQueryService(
            EventRepository eventRepository,
            EventQueryRepository eventQueryRepository,
            RouteKeyResolver routeKeyResolver,
            CanonicalEventTypeResolver canonicalEventTypeResolver
    ) {
        this.overviewAnalyticsService = new OverviewAnalyticsService(
                eventQueryRepository,
                routeKeyResolver,
                canonicalEventTypeResolver
        );
        this.aggregateAnalyticsService = new AggregateAnalyticsService(
                eventRepository,
                eventQueryRepository,
                routeKeyResolver,
                canonicalEventTypeResolver
        );
        this.trendAnalyticsService = new TrendAnalyticsService(
                eventQueryRepository,
                routeKeyResolver,
                canonicalEventTypeResolver
        );
    }

    @Transactional(readOnly = true)
    public long countByEventType(String eventType) {
        return aggregateAnalyticsService.countByEventType(eventType);
    }

    @Transactional(readOnly = true)
    public OverviewResponse getOverview(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        return overviewAnalyticsService.getOverview(from, to, organizationId, externalUserId, eventType);
    }

    @Transactional(readOnly = true)
    public List<PathCountProjection> countByPathBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        return aggregateAnalyticsService.countByPathBetween(from, to, organizationId, externalUserId, eventType, top);
    }

    @Transactional(readOnly = true)
    public List<RawEventTypeItem> countRawEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            int top
    ) {
        return aggregateAnalyticsService.countRawEventTypeBetween(from, to, organizationId, externalUserId, top);
    }

    @Transactional(readOnly = true)
    public List<CanonicalEventTypeItem> countByCanonicalEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            int top
    ) {
        return aggregateAnalyticsService.countByCanonicalEventTypeBetween(from, to, organizationId, externalUserId, top);
    }

    @Transactional(readOnly = true)
    public Double eventTypeMappingCoverageBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        return overviewAnalyticsService.eventTypeMappingCoverageBetween(from, to, organizationId, externalUserId);
    }

    @Transactional(readOnly = true)
    public Double routeMatchCoverageBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType
    ) {
        return overviewAnalyticsService.routeMatchCoverageBetween(from, to, organizationId, externalUserId, eventType);
    }

    @Transactional(readOnly = true)
    public List<RouteAggregateItem> countByRouteKeyBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        return aggregateAnalyticsService.countByRouteKeyBetween(from, to, organizationId, externalUserId, eventType, top);
    }

    @Transactional(readOnly = true)
    public List<UnmatchedPathItem> countUnmatchedPathsBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        return aggregateAnalyticsService.countUnmatchedPathsBetween(from, to, organizationId, externalUserId, eventType, top);
    }

    @Transactional(readOnly = true)
    public List<RouteEventTypeAggregateItem> countByRouteKeyAndCanonicalEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            int top
    ) {
        return aggregateAnalyticsService.countByRouteKeyAndCanonicalEventTypeBetween(from, to, organizationId, externalUserId, top);
    }

    @Transactional(readOnly = true)
    public List<RouteUniqueUserItem> countUniqueUsersByRouteKeyBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        return aggregateAnalyticsService.countUniqueUsersByRouteKeyBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType,
                top
        );
    }

    @Transactional(readOnly = true)
    public List<CanonicalEventTypeUniqueUserItem> countUniqueUsersByCanonicalEventTypeBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            int top
    ) {
        return aggregateAnalyticsService.countUniqueUsersByCanonicalEventTypeBetween(from, to, organizationId, externalUserId, top);
    }

    @Transactional(readOnly = true)
    public List<TimeBucketCountProjection> countByTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket,
            String timezone
    ) {
        return trendAnalyticsService.countByTimeBucketBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType,
                bucket,
                timezone
        );
    }

    @Transactional(readOnly = true)
    public List<RouteTimeBucketItem> countByRouteKeyTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket
    ) {
        return trendAnalyticsService.countByRouteKeyTimeBucketBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType,
                bucket
        );
    }

    @Transactional(readOnly = true)
    public List<RouteTimeBucketItem> countByRouteKeyTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket,
            String timezone
    ) {
        return trendAnalyticsService.countByRouteKeyTimeBucketBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType,
                bucket,
                timezone
        );
    }

    @Transactional(readOnly = true)
    public List<CanonicalEventTypeTimeBucketItem> countByCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket
    ) {
        return trendAnalyticsService.countByCanonicalEventTypeTimeBucketBetween(
                from,
                to,
                organizationId,
                externalUserId,
                bucket
        );
    }

    @Transactional(readOnly = true)
    public List<CanonicalEventTypeTimeBucketItem> countByCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        return trendAnalyticsService.countByCanonicalEventTypeTimeBucketBetween(
                from,
                to,
                organizationId,
                externalUserId,
                bucket,
                timezone
        );
    }

    @Transactional(readOnly = true)
    public List<RouteEventTypeTimeBucketItem> countByRouteKeyAndCanonicalEventTypeTimeBucketBetween(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            TimeBucket bucket,
            String timezone
    ) {
        return trendAnalyticsService.countByRouteKeyAndCanonicalEventTypeTimeBucketBetween(
                from,
                to,
                organizationId,
                externalUserId,
                bucket,
                timezone
        );
    }
}
