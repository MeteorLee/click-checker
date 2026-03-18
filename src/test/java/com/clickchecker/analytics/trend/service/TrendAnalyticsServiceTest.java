package com.clickchecker.analytics.trend.service;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.trend.controller.response.CanonicalEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteTimeBucketItem;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.RawEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.RawPathOccurredAtCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
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

class TrendAnalyticsServiceTest {

    private final EventQueryRepository eventQueryRepository = mock(EventQueryRepository.class);
    private final RouteKeyResolver routeKeyResolver = mock(RouteKeyResolver.class);
    private final CanonicalEventTypeResolver canonicalEventTypeResolver = mock(CanonicalEventTypeResolver.class);

    private final TrendAnalyticsService trendAnalyticsService =
            new TrendAnalyticsService(
                    eventQueryRepository,
                    routeKeyResolver,
                    canonicalEventTypeResolver
            );

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

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "/posts/1", "/posts/{id}",
                        "/posts/2", "/posts/{id}",
                        "/landing", "/landing"
                ));

        List<RouteTimeBucketItem> result =
                trendAnalyticsService.countByRouteKeyTimeBucketBetween(from, to, 1L, null, "click", TimeBucket.HOUR);

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

        when(canonicalEventTypeResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "button_click", "click",
                        "post_click", "click",
                        "page_view", "view",
                        "mystery_event", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                ));

        List<CanonicalEventTypeTimeBucketItem> result =
                trendAnalyticsService.countByCanonicalEventTypeTimeBucketBetween(from, to, 1L, null, TimeBucket.HOUR);

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

        when(eventQueryRepository.countRawOccurredAtBetween(from, to, 1L, null, null))
                .thenReturn(List.of(
                        new RawOccurredAtCountProjection(Instant.parse("2026-03-02T00:30:00Z"), 3)
                ));

        List<TimeBucketCountProjection> result =
                trendAnalyticsService.countByTimeBucketBetween(from, to, 1L, null, null, TimeBucket.DAY, "Asia/Seoul");

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

        when(eventQueryRepository.countRawPathOccurredAtBetween(from, to, 1L, null, "click"))
                .thenReturn(List.of(
                        new RawPathOccurredAtCountProjection("/posts/1", Instant.parse("2026-03-02T00:30:00Z"), 2)
                ));

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of("/posts/1", "/posts/{id}"));

        List<RouteTimeBucketItem> result =
                trendAnalyticsService.countByRouteKeyTimeBucketBetween(
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

        when(eventQueryRepository.countRawEventTypeOccurredAtBetween(from, to, 1L, null))
                .thenReturn(List.of(
                        new RawEventTypeOccurredAtCountProjection("button_click", Instant.parse("2026-03-02T00:30:00Z"), 2)
                ));

        when(canonicalEventTypeResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of("button_click", "click"));

        List<CanonicalEventTypeTimeBucketItem> result =
                trendAnalyticsService.countByCanonicalEventTypeTimeBucketBetween(
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

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "/posts/1", "/posts/{id}",
                        "/landing", "/landing"
                ));
        when(canonicalEventTypeResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "button_click", "click",
                        "page_view", "view"
                ));

        List<RouteEventTypeTimeBucketItem> result =
                trendAnalyticsService.countByRouteKeyAndCanonicalEventTypeTimeBucketBetween(
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
