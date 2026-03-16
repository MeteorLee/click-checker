package com.clickchecker.event.service;

import com.clickchecker.event.controller.response.CanonicalEventTypeItem;
import com.clickchecker.event.controller.response.CanonicalEventTypeTimeBucketItem;
import com.clickchecker.event.controller.response.CanonicalEventTypeUniqueUserItem;
import com.clickchecker.event.controller.response.OverviewResponse;
import com.clickchecker.event.controller.response.RouteAggregateItem;
import com.clickchecker.event.controller.response.RouteEventTypeAggregateItem;
import com.clickchecker.event.controller.response.RouteEventTypeTimeBucketItem;
import com.clickchecker.event.controller.response.RouteTimeBucketItem;
import com.clickchecker.event.controller.response.RouteUniqueUserItem;
import com.clickchecker.event.model.TimeBucket;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeUserCountProjection;
import com.clickchecker.event.repository.projection.RawOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathUserCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import com.clickchecker.route.service.RouteKeyResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventQueryServiceTest {

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventQueryRepository eventQueryRepository = mock(EventQueryRepository.class);
    private final RouteKeyResolver routeKeyResolver = mock(RouteKeyResolver.class);
    private final CanonicalEventTypeResolver canonicalEventTypeResolver = mock(CanonicalEventTypeResolver.class);

    private final EventQueryService eventQueryService =
            new EventQueryService(
                    eventRepository,
                    eventQueryRepository,
                    routeKeyResolver,
                    canonicalEventTypeResolver
            );

    @Test
    void countByRouteKeyBetween_aggregatesByResolvedRouteKey_beforeApplyingTopLimit() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");

        when(eventQueryRepository.countRawPathBetween(from, to, 1L, null, "click"))
                .thenReturn(List.of(
                        new PathCountProjection("/posts/1", 5),
                        new PathCountProjection("/posts/2", 4),
                        new PathCountProjection("/landing", 8)
                ));

        when(routeKeyResolver.resolve(1L, "/posts/1")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/posts/2")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/landing")).thenReturn("/landing");

        List<RouteAggregateItem> result =
                eventQueryService.countByRouteKeyBetween(from, to, 1L, null, "click", 1);

        assertThat(result)
                .containsExactly(new RouteAggregateItem("/posts/{id}", 9));
    }

    @Test
    void getOverview_buildsSummaryAndUsesNullDeltaRateWhenPreviousBaselineIsMissing() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");
        Instant previousFrom = Instant.parse("2026-02-28T00:00:00Z");

        when(eventQueryRepository.countBetween(from, to, 1L, null, null))
                .thenReturn(3L);
        when(eventQueryRepository.countBetween(previousFrom, from, 1L, null, null))
                .thenReturn(0L);
        when(eventQueryRepository.countUniqueUsersBetween(from, to, 1L, null, null))
                .thenReturn(2L);
        when(eventQueryRepository.countIdentifiedEventsBetween(from, to, 1L, null, null))
                .thenReturn(2L);
        when(eventQueryRepository.countRawPathBetween(from, to, 1L, null, null))
                .thenReturn(List.of(
                        new PathCountProjection("/posts/1", 2),
                        new PathCountProjection("/landing", 1)
                ));
        when(eventQueryRepository.countRawEventTypeBetween(from, to, 1L, null, Integer.MAX_VALUE))
                .thenReturn(List.of(
                        new RawEventTypeCountProjection("button_click", 2),
                        new RawEventTypeCountProjection("page_view", 1)
                ));

        when(routeKeyResolver.resolve(1L, "/posts/1")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/landing")).thenReturn("/landing");
        when(canonicalEventTypeResolver.resolve(1L, "button_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "page_view")).thenReturn("view");

        OverviewResponse result = eventQueryService.getOverview(from, to, 1L, null, null);

        assertThat(result.totalEvents()).isEqualTo(3);
        assertThat(result.uniqueUsers()).isEqualTo(2);
        assertThat(result.identifiedEventRate()).isEqualTo(2.0 / 3.0);
        assertThat(result.comparison().current()).isEqualTo(3);
        assertThat(result.comparison().previous()).isZero();
        assertThat(result.comparison().delta()).isEqualTo(3);
        assertThat(result.comparison().deltaRate()).isNull();
        assertThat(result.comparison().hasPreviousBaseline()).isFalse();
        assertThat(result.topRoutes())
                .containsExactly(
                        new OverviewResponse.RouteSummary("/posts/{id}", 2),
                        new OverviewResponse.RouteSummary("/landing", 1)
                );
        assertThat(result.topEventTypes())
                .containsExactly(
                        new OverviewResponse.EventTypeSummary("click", 2),
                        new OverviewResponse.EventTypeSummary("view", 1)
                );
    }

    @Test
    void countByCanonicalEventTypeBetween_aggregatesByResolvedCanonicalEventType_beforeApplyingTopLimit() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");

        when(eventQueryRepository.countRawEventTypeBetween(from, to, 1L, null, Integer.MAX_VALUE))
                .thenReturn(List.of(
                        new RawEventTypeCountProjection("button_click", 5),
                        new RawEventTypeCountProjection("post_click", 4),
                        new RawEventTypeCountProjection("mystery_event", 3)
                ));

        when(canonicalEventTypeResolver.resolve(1L, "button_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "post_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "mystery_event"))
                .thenReturn(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE);

        List<CanonicalEventTypeItem> result =
                eventQueryService.countByCanonicalEventTypeBetween(from, to, 1L, null, 2);

        assertThat(result).containsExactly(
                new CanonicalEventTypeItem("click", 9),
                new CanonicalEventTypeItem(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, 3)
        );
    }

    @Test
    void countUniqueUsersByCanonicalEventTypeBetween_aggregatesDistinctUsersAfterResolvingCanonicalEventType() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");

        when(eventQueryRepository.countRawEventTypeUserBetween(from, to, 1L, null))
                .thenReturn(List.of(
                        new RawEventTypeUserCountProjection("button_click", 101L, 2L),
                        new RawEventTypeUserCountProjection("post_click", 102L, 1L),
                        new RawEventTypeUserCountProjection("page_view", 201L, 3L)
                ));

        when(canonicalEventTypeResolver.resolve(1L, "button_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "post_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "page_view")).thenReturn("view");

        List<CanonicalEventTypeUniqueUserItem> result =
                eventQueryService.countUniqueUsersByCanonicalEventTypeBetween(from, to, 1L, null, 10);

        assertThat(result).containsExactly(
                new CanonicalEventTypeUniqueUserItem("click", 2),
                new CanonicalEventTypeUniqueUserItem("view", 1)
        );
    }

    @Test
    void countByRouteKeyAndCanonicalEventTypeBetween_aggregatesByBothResolvedAxes_beforeApplyingTopLimit() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");

        when(eventQueryRepository.countRawPathEventTypeBetween(from, to, 1L, null))
                .thenReturn(List.of(
                        new RawPathEventTypeCountProjection("/posts/1", "button_click", 5),
                        new RawPathEventTypeCountProjection("/posts/2", "post_click", 4),
                        new RawPathEventTypeCountProjection("/landing", "page_view", 3),
                        new RawPathEventTypeCountProjection("/landing", "mystery_event", 2)
                ));

        when(routeKeyResolver.resolve(1L, "/posts/1")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/posts/2")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/landing")).thenReturn("/landing");

        when(canonicalEventTypeResolver.resolve(1L, "button_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "post_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "page_view")).thenReturn("view");
        when(canonicalEventTypeResolver.resolve(1L, "mystery_event"))
                .thenReturn(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE);

        List<RouteEventTypeAggregateItem> result =
                eventQueryService.countByRouteKeyAndCanonicalEventTypeBetween(from, to, 1L, null, 3);

        assertThat(result).containsExactly(
                new RouteEventTypeAggregateItem("/posts/{id}", "click", 9),
                new RouteEventTypeAggregateItem("/landing", "view", 3),
                new RouteEventTypeAggregateItem("/landing", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, 2)
        );
    }

    @Test
    void countUniqueUsersByRouteKeyBetween_aggregatesDistinctUsersAfterResolvingRouteKey() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");

        when(eventQueryRepository.countRawPathUserBetween(from, to, 1L, null, "click"))
                .thenReturn(List.of(
                        new RawPathUserCountProjection("/posts/1", 101L, 2L),
                        new RawPathUserCountProjection("/posts/2", 102L, 1L),
                        new RawPathUserCountProjection("/landing", 201L, 3L)
                ));

        when(routeKeyResolver.resolve(1L, "/posts/1")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/posts/2")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/landing")).thenReturn("/landing");

        List<RouteUniqueUserItem> result =
                eventQueryService.countUniqueUsersByRouteKeyBetween(from, to, 1L, null, "click", 10);

        assertThat(result).containsExactly(
                new RouteUniqueUserItem("/posts/{id}", 2),
                new RouteUniqueUserItem("/landing", 1)
        );
    }

    @Test
    void countByRouteKeyTimeBucketBetween_aggregatesByResolvedRouteKey_withinEachBucket() {
        Instant from = Instant.parse("2026-03-01T10:00:00Z");
        Instant to = Instant.parse("2026-03-01T12:00:00Z");
        Instant bucket10 = Instant.parse("2026-03-01T10:00:00Z");
        Instant bucket11 = Instant.parse("2026-03-01T11:00:00Z");

        when(eventQueryRepository.countRawPathOccurredAtBetween(from, to, 1L, null, "click"))
                .thenReturn(List.of(
                        new RawPathOccurredAtCountProjection("/posts/1", Instant.parse("2026-03-01T10:10:00Z"), 5),
                        new RawPathOccurredAtCountProjection("/posts/2", Instant.parse("2026-03-01T10:20:00Z"), 4),
                        new RawPathOccurredAtCountProjection("/landing", Instant.parse("2026-03-01T11:05:00Z"), 3)
                ));

        when(routeKeyResolver.resolve(1L, "/posts/1")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/posts/2")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/landing")).thenReturn("/landing");

        List<RouteTimeBucketItem> result =
                eventQueryService.countByRouteKeyTimeBucketBetween(from, to, 1L, null, "click", TimeBucket.HOUR);

        assertThat(result).containsExactly(
                new RouteTimeBucketItem("/landing", bucket10, 0),
                new RouteTimeBucketItem("/posts/{id}", bucket10, 9),
                new RouteTimeBucketItem("/landing", bucket11, 3),
                new RouteTimeBucketItem("/posts/{id}", bucket11, 0)
        );
    }

    @Test
    void countByCanonicalEventTypeTimeBucketBetween_aggregatesByResolvedCanonicalEventType_withinEachBucket() {
        Instant from = Instant.parse("2026-03-01T10:00:00Z");
        Instant to = Instant.parse("2026-03-01T12:00:00Z");
        Instant bucket10 = Instant.parse("2026-03-01T10:00:00Z");
        Instant bucket11 = Instant.parse("2026-03-01T11:00:00Z");

        when(eventQueryRepository.countRawEventTypeOccurredAtBetween(from, to, 1L, null))
                .thenReturn(List.of(
                        new RawEventTypeOccurredAtCountProjection("button_click", Instant.parse("2026-03-01T10:10:00Z"), 5),
                        new RawEventTypeOccurredAtCountProjection("post_click", Instant.parse("2026-03-01T10:20:00Z"), 4),
                        new RawEventTypeOccurredAtCountProjection("page_view", Instant.parse("2026-03-01T11:05:00Z"), 3),
                        new RawEventTypeOccurredAtCountProjection("mystery_event", Instant.parse("2026-03-01T11:15:00Z"), 1)
                ));

        when(canonicalEventTypeResolver.resolve(1L, "button_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "post_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "page_view")).thenReturn("view");
        when(canonicalEventTypeResolver.resolve(1L, "mystery_event"))
                .thenReturn(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE);

        List<CanonicalEventTypeTimeBucketItem> result =
                eventQueryService.countByCanonicalEventTypeTimeBucketBetween(from, to, 1L, null, TimeBucket.HOUR);

        assertThat(result).containsExactly(
                new CanonicalEventTypeTimeBucketItem(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, bucket10, 0),
                new CanonicalEventTypeTimeBucketItem("click", bucket10, 9),
                new CanonicalEventTypeTimeBucketItem("view", bucket10, 0),
                new CanonicalEventTypeTimeBucketItem(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, bucket11, 1),
                new CanonicalEventTypeTimeBucketItem("click", bucket11, 0),
                new CanonicalEventTypeTimeBucketItem("view", bucket11, 3)
        );
    }

    @Test
    void countByTimeBucketBetween_fillsMissingDayBuckets_usingRequestedTimezone() {
        Instant from = Instant.parse("2026-03-01T15:00:00Z");
        Instant to = Instant.parse("2026-03-03T15:00:00Z");
        Instant bucketStartKstDay1 = Instant.parse("2026-03-01T15:00:00Z");
        Instant bucketStartKstDay2 = Instant.parse("2026-03-02T15:00:00Z");

        when(eventQueryRepository.countRawOccurredAtBetween(
                from,
                to,
                1L,
                null,
                null
        )).thenReturn(List.of(
                new RawOccurredAtCountProjection(Instant.parse("2026-03-02T00:30:00Z"), 3)
        ));

        List<TimeBucketCountProjection> result =
                eventQueryService.countByTimeBucketBetween(from, to, 1L, null, null, TimeBucket.DAY, "Asia/Seoul");

        assertThat(result).containsExactly(
                new TimeBucketCountProjection(bucketStartKstDay1, 3),
                new TimeBucketCountProjection(bucketStartKstDay2, 0)
        );
    }

    @Test
    void countByRouteKeyTimeBucketBetween_fillsMissingDayBuckets_usingRequestedTimezone() {
        Instant from = Instant.parse("2026-03-01T15:00:00Z");
        Instant to = Instant.parse("2026-03-03T15:00:00Z");
        Instant bucketStartKstDay1 = Instant.parse("2026-03-01T15:00:00Z");
        Instant bucketStartKstDay2 = Instant.parse("2026-03-02T15:00:00Z");

        when(eventQueryRepository.countRawPathOccurredAtBetween(
                from,
                to,
                1L,
                null,
                "click"
        )).thenReturn(List.of(
                new RawPathOccurredAtCountProjection("/posts/1", Instant.parse("2026-03-02T00:30:00Z"), 2)
        ));

        when(routeKeyResolver.resolve(1L, "/posts/1")).thenReturn("/posts/{id}");

        List<RouteTimeBucketItem> result =
                eventQueryService.countByRouteKeyTimeBucketBetween(
                        from,
                        to,
                        1L,
                        null,
                        "click",
                        TimeBucket.DAY,
                        "Asia/Seoul"
                );

        assertThat(result).containsExactly(
                new RouteTimeBucketItem("/posts/{id}", bucketStartKstDay1, 2),
                new RouteTimeBucketItem("/posts/{id}", bucketStartKstDay2, 0)
        );
    }

    @Test
    void countByCanonicalEventTypeTimeBucketBetween_fillsMissingDayBuckets_usingRequestedTimezone() {
        Instant from = Instant.parse("2026-03-01T15:00:00Z");
        Instant to = Instant.parse("2026-03-03T15:00:00Z");
        Instant bucketStartKstDay1 = Instant.parse("2026-03-01T15:00:00Z");
        Instant bucketStartKstDay2 = Instant.parse("2026-03-02T15:00:00Z");

        when(eventQueryRepository.countRawEventTypeOccurredAtBetween(
                from,
                to,
                1L,
                null
        )).thenReturn(List.of(
                new RawEventTypeOccurredAtCountProjection("button_click", Instant.parse("2026-03-02T00:30:00Z"), 2)
        ));

        when(canonicalEventTypeResolver.resolve(1L, "button_click")).thenReturn("click");

        List<CanonicalEventTypeTimeBucketItem> result =
                eventQueryService.countByCanonicalEventTypeTimeBucketBetween(
                        from,
                        to,
                        1L,
                        null,
                        TimeBucket.DAY,
                        "Asia/Seoul"
                );

        assertThat(result).containsExactly(
                new CanonicalEventTypeTimeBucketItem("click", bucketStartKstDay1, 2),
                new CanonicalEventTypeTimeBucketItem("click", bucketStartKstDay2, 0)
        );
    }

    @Test
    void countByRouteKeyAndCanonicalEventTypeTimeBucketBetween_fillsMissingBuckets_forResolvedAxes() {
        Instant from = Instant.parse("2026-03-01T10:00:00Z");
        Instant to = Instant.parse("2026-03-01T12:00:00Z");
        Instant bucket10 = Instant.parse("2026-03-01T10:00:00Z");
        Instant bucket11 = Instant.parse("2026-03-01T11:00:00Z");

        when(eventQueryRepository.countRawPathEventTypeOccurredAtBetween(from, to, 1L, null))
                .thenReturn(List.of(
                        new RawPathEventTypeOccurredAtCountProjection("/posts/1", "button_click", Instant.parse("2026-03-01T10:10:00Z"), 2),
                        new RawPathEventTypeOccurredAtCountProjection("/landing", "page_view", Instant.parse("2026-03-01T11:15:00Z"), 1)
                ));

        when(routeKeyResolver.resolve(1L, "/posts/1")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/landing")).thenReturn("/landing");
        when(canonicalEventTypeResolver.resolve(1L, "button_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "page_view")).thenReturn("view");

        List<RouteEventTypeTimeBucketItem> result =
                eventQueryService.countByRouteKeyAndCanonicalEventTypeTimeBucketBetween(
                        from,
                        to,
                        1L,
                        null,
                        TimeBucket.HOUR,
                        "UTC"
                );

        assertThat(result).containsExactly(
                new RouteEventTypeTimeBucketItem("/landing", "view", bucket10, 0),
                new RouteEventTypeTimeBucketItem("/posts/{id}", "click", bucket10, 2),
                new RouteEventTypeTimeBucketItem("/landing", "view", bucket11, 1),
                new RouteEventTypeTimeBucketItem("/posts/{id}", "click", bucket11, 0)
        );
    }
}
