package com.clickchecker.analytics.aggregate.service;

import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeItem;
import com.clickchecker.analytics.aggregate.controller.response.CanonicalEventTypeUniqueUserItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteAggregateItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteEventTypeAggregateItem;
import com.clickchecker.analytics.aggregate.controller.response.RouteUniqueUserItem;
import com.clickchecker.analytics.aggregate.controller.response.UnmatchedPathItem;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeUserProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeCountProjection;
import com.clickchecker.event.repository.projection.RawPathUserProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import com.clickchecker.route.service.RouteKeyResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AggregateAnalyticsServiceTest {

    private final EventQueryRepository eventQueryRepository = mock(EventQueryRepository.class);
    private final RouteKeyResolver routeKeyResolver = mock(RouteKeyResolver.class);
    private final CanonicalEventTypeResolver canonicalEventTypeResolver = mock(CanonicalEventTypeResolver.class);

    private final AggregateAnalyticsService aggregateAnalyticsService =
            new AggregateAnalyticsService(
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

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "/posts/1", "/posts/{id}",
                        "/posts/2", "/posts/{id}",
                        "/landing", "/landing"
                ));

        List<RouteAggregateItem> result =
                aggregateAnalyticsService.countByRouteKeyBetween(from, to, 1L, null, "click", 1);

        assertThat(result).containsExactly(new RouteAggregateItem("/posts/{id}", 9));
    }

    @Test
    void countUnmatchedPathsBetween_returnsOnlyRawPathsResolvedAsUnmatchedRoute() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");

        when(eventQueryRepository.countRawPathBetween(from, to, 1L, null, "click"))
                .thenReturn(List.of(
                        new PathCountProjection("/posts/1", 5),
                        new PathCountProjection("/unknown/a", 4),
                        new PathCountProjection("/unknown/b", 6)
                ));

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "/posts/1", "/posts/{id}",
                        "/unknown/a", RouteKeyResolver.UNMATCHED_ROUTE,
                        "/unknown/b", RouteKeyResolver.UNMATCHED_ROUTE
                ));

        List<UnmatchedPathItem> result =
                aggregateAnalyticsService.countUnmatchedPathsBetween(from, to, 1L, null, "click", 10);

        assertThat(result).containsExactly(
                new UnmatchedPathItem("/unknown/b", 6),
                new UnmatchedPathItem("/unknown/a", 4)
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

        when(canonicalEventTypeResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "button_click", "click",
                        "post_click", "click",
                        "mystery_event", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                ));

        List<CanonicalEventTypeItem> result =
                aggregateAnalyticsService.countByCanonicalEventTypeBetween(from, to, 1L, null, 2);

        assertThat(result).containsExactly(
                new CanonicalEventTypeItem("click", 9),
                new CanonicalEventTypeItem(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, 3)
        );
    }

    @Test
    void countUniqueUsersByCanonicalEventTypeBetween_aggregatesDistinctUsersAfterResolvingCanonicalEventType() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");

        when(eventQueryRepository.findDistinctRawEventTypeUserPairsBetween(from, to, 1L, null))
                .thenReturn(List.of(
                        new RawEventTypeUserProjection("button_click", 101L),
                        new RawEventTypeUserProjection("post_click", 102L),
                        new RawEventTypeUserProjection("page_view", 201L)
                ));

        when(canonicalEventTypeResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "button_click", "click",
                        "post_click", "click",
                        "page_view", "view"
                ));

        List<CanonicalEventTypeUniqueUserItem> result =
                aggregateAnalyticsService.countUniqueUsersByCanonicalEventTypeBetween(from, to, 1L, null, 10);

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

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "/posts/1", "/posts/{id}",
                        "/posts/2", "/posts/{id}",
                        "/landing", "/landing"
                ));
        when(canonicalEventTypeResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "button_click", "click",
                        "post_click", "click",
                        "page_view", "view",
                        "mystery_event", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                ));

        List<RouteEventTypeAggregateItem> result =
                aggregateAnalyticsService.countByRouteKeyAndCanonicalEventTypeBetween(from, to, 1L, null, 3);

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

        when(eventQueryRepository.findDistinctRawPathUserPairsBetween(from, to, 1L, null, "click"))
                .thenReturn(List.of(
                        new RawPathUserProjection("/posts/1", 101L),
                        new RawPathUserProjection("/posts/2", 102L),
                        new RawPathUserProjection("/landing", 201L)
                ));

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "/posts/1", "/posts/{id}",
                        "/posts/2", "/posts/{id}",
                        "/landing", "/landing"
                ));

        List<RouteUniqueUserItem> result =
                aggregateAnalyticsService.countUniqueUsersByRouteKeyBetween(from, to, 1L, null, "click", 10);

        assertThat(result).containsExactly(
                new RouteUniqueUserItem("/posts/{id}", 2),
                new RouteUniqueUserItem("/landing", 1)
        );
    }
}
