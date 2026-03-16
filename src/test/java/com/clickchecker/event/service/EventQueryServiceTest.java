package com.clickchecker.event.service;

import com.clickchecker.event.controller.response.OverviewResponse;
import com.clickchecker.event.controller.response.RouteAggregateItem;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.event.repository.projection.EventTypeCountProjection;
import com.clickchecker.event.repository.projection.PathCountProjection;
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

    private final EventQueryService eventQueryService =
            new EventQueryService(eventRepository, eventQueryRepository, routeKeyResolver);

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
        when(eventQueryRepository.countByEventTypeBetween(from, to, 1L, null, null, 3))
                .thenReturn(List.of(
                        new EventTypeCountProjection("click", 2),
                        new EventTypeCountProjection("view", 1)
                ));

        when(routeKeyResolver.resolve(1L, "/posts/1")).thenReturn("/posts/{id}");
        when(routeKeyResolver.resolve(1L, "/landing")).thenReturn("/landing");

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
}
