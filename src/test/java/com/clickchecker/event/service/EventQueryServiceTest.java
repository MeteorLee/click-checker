package com.clickchecker.event.service;

import com.clickchecker.event.controller.response.CanonicalEventTypeItem;
import com.clickchecker.event.controller.response.CanonicalEventTypeTimeBucketItem;
import com.clickchecker.event.controller.response.OverviewResponse;
import com.clickchecker.event.controller.response.RouteAggregateItem;
import com.clickchecker.event.controller.response.RouteEventTypeAggregateItem;
import com.clickchecker.event.controller.response.RouteTimeBucketItem;
import com.clickchecker.event.model.TimeBucket;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawPathTimeBucketCountProjection;
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
    void countByRouteKeyTimeBucketBetween_aggregatesByResolvedRouteKey_withinEachBucket() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");
        Instant bucket10 = Instant.parse("2026-03-01T10:00:00Z");
        Instant bucket11 = Instant.parse("2026-03-01T11:00:00Z");

        when(eventQueryRepository.countRawPathTimeBucketBetween(from, to, 1L, null, "click", TimeBucket.HOUR))
                .thenReturn(List.of(
                        new RawPathTimeBucketCountProjection("/posts/1", bucket10, 5),
                        new RawPathTimeBucketCountProjection("/posts/2", bucket10, 4),
                        new RawPathTimeBucketCountProjection("/landing", bucket11, 3)
                ));

        when(routeKeyResolver.resolve(1L, "/posts/1")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/posts/2")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/landing")).thenReturn("/landing");

        List<RouteTimeBucketItem> result =
                eventQueryService.countByRouteKeyTimeBucketBetween(from, to, 1L, null, "click", TimeBucket.HOUR);

        assertThat(result).containsExactly(
                new RouteTimeBucketItem("/posts/{id}", bucket10, 9),
                new RouteTimeBucketItem("/landing", bucket11, 3)
        );
    }

    @Test
    void countByCanonicalEventTypeTimeBucketBetween_aggregatesByResolvedCanonicalEventType_withinEachBucket() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");
        Instant bucket10 = Instant.parse("2026-03-01T10:00:00Z");
        Instant bucket11 = Instant.parse("2026-03-01T11:00:00Z");

        when(eventQueryRepository.countRawEventTypeTimeBucketBetween(from, to, 1L, null, TimeBucket.HOUR))
                .thenReturn(List.of(
                        new RawEventTypeTimeBucketCountProjection("button_click", bucket10, 5),
                        new RawEventTypeTimeBucketCountProjection("post_click", bucket10, 4),
                        new RawEventTypeTimeBucketCountProjection("page_view", bucket11, 3),
                        new RawEventTypeTimeBucketCountProjection("mystery_event", bucket11, 1)
                ));

        when(canonicalEventTypeResolver.resolve(1L, "button_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "post_click")).thenReturn("click");
        when(canonicalEventTypeResolver.resolve(1L, "page_view")).thenReturn("view");
        when(canonicalEventTypeResolver.resolve(1L, "mystery_event"))
                .thenReturn(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE);

        List<CanonicalEventTypeTimeBucketItem> result =
                eventQueryService.countByCanonicalEventTypeTimeBucketBetween(from, to, 1L, null, TimeBucket.HOUR);

        assertThat(result).containsExactly(
                new CanonicalEventTypeTimeBucketItem("click", bucket10, 9),
                new CanonicalEventTypeTimeBucketItem(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, bucket11, 1),
                new CanonicalEventTypeTimeBucketItem("view", bucket11, 3)
        );
    }
}
