package com.clickchecker.analytics.activity.service;

import com.clickchecker.analytics.activity.controller.response.ActivityOverviewResponse;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.PathCountProjection;
import com.clickchecker.event.repository.projection.RawEventTypeCountProjection;
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

class ActivityAnalyticsServiceTest {

    private final EventQueryRepository eventQueryRepository = mock(EventQueryRepository.class);
    private final RouteKeyResolver routeKeyResolver = mock(RouteKeyResolver.class);
    private final CanonicalEventTypeResolver canonicalEventTypeResolver = mock(CanonicalEventTypeResolver.class);

    private final ActivityAnalyticsService activityAnalyticsService =
            new ActivityAnalyticsService(
                    eventQueryRepository,
                    routeKeyResolver,
                    canonicalEventTypeResolver
            );

    @Test
    void getOverview_buildsSummaryAndUsesNullDeltaRateWhenPreviousBaselineIsMissing() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");
        Instant previousFrom = Instant.parse("2026-02-28T00:00:00Z");

        when(eventQueryRepository.countBetween(from, to, 1L, null, null)).thenReturn(3L);
        when(eventQueryRepository.countBetween(previousFrom, from, 1L, null, null)).thenReturn(0L);
        when(eventQueryRepository.countUniqueUsersBetween(from, to, 1L, null, null)).thenReturn(2L);
        when(eventQueryRepository.countIdentifiedEventsBetween(from, to, 1L, null, null)).thenReturn(2L);
        when(eventQueryRepository.countEventsWithEventTypeBetween(from, to, 1L, null)).thenReturn(3L);
        when(eventQueryRepository.countEventsWithPathBetween(from, to, 1L, null, null)).thenReturn(3L);
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

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of("/posts/1", "/posts/{id}", "/landing", "/landing"));
        when(canonicalEventTypeResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of("button_click", "click", "page_view", "view"));

        ActivityOverviewResponse result = activityAnalyticsService.getOverview(from, to, 1L, null, null);

        assertThat(result.totalEvents()).isEqualTo(3);
        assertThat(result.uniqueUsers()).isEqualTo(2);
        assertThat(result.identifiedEventRate()).isEqualTo(2.0 / 3.0);
        assertThat(result.eventTypeMappingCoverage()).isEqualTo(1.0);
        assertThat(result.routeMatchCoverage()).isEqualTo(1.0);
        assertThat(result.comparison().current()).isEqualTo(3);
        assertThat(result.comparison().previous()).isZero();
        assertThat(result.comparison().delta()).isEqualTo(3);
        assertThat(result.comparison().deltaRate()).isNull();
        assertThat(result.comparison().hasPreviousBaseline()).isFalse();
        assertThat(result.topRoutes()).containsExactly(
                new ActivityOverviewResponse.RouteSummary("/posts/{id}", 2),
                new ActivityOverviewResponse.RouteSummary("/landing", 1)
        );
        assertThat(result.topEventTypes()).containsExactly(
                new ActivityOverviewResponse.EventTypeSummary("click", 2),
                new ActivityOverviewResponse.EventTypeSummary("view", 1)
        );
    }

    @Test
    void eventTypeMappingCoverageBetween_returnsMappedEventRatio() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");

        when(eventQueryRepository.countEventsWithEventTypeBetween(from, to, 1L, null)).thenReturn(10L);
        when(eventQueryRepository.countRawEventTypeBetween(from, to, 1L, null, Integer.MAX_VALUE))
                .thenReturn(List.of(
                        new RawEventTypeCountProjection("button_click", 6),
                        new RawEventTypeCountProjection("mystery_event", 4)
                ));

        when(canonicalEventTypeResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "button_click", "click",
                        "mystery_event", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                ));

        Double result = activityAnalyticsService.eventTypeMappingCoverageBetween(from, to, 1L, null);

        assertThat(result).isEqualTo(0.6);
    }

    @Test
    void routeMatchCoverageBetween_returnsMatchedRouteRatio() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-02T00:00:00Z");

        when(eventQueryRepository.countEventsWithPathBetween(from, to, 1L, null, "click")).thenReturn(10L);
        when(eventQueryRepository.countRawPathBetween(from, to, 1L, null, "click"))
                .thenReturn(List.of(
                        new PathCountProjection("/posts/1", 6),
                        new PathCountProjection("/unknown/1", 4)
                ));

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "/posts/1", "/posts/{id}",
                        "/unknown/1", RouteKeyResolver.UNMATCHED_ROUTE
                ));

        Double result = activityAnalyticsService.routeMatchCoverageBetween(from, to, 1L, null, "click");

        assertThat(result).isEqualTo(0.6);
    }
}
