package com.clickchecker.analytics.trend.service;

import com.clickchecker.analytics.common.model.TimeBucket;
import com.clickchecker.analytics.trend.controller.response.CanonicalEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteEventTypeTimeBucketItem;
import com.clickchecker.analytics.trend.controller.response.RouteTimeBucketItem;
import com.clickchecker.event.repository.EventTrendNativeQueryRepository;
import com.clickchecker.event.repository.projection.RawEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathEventTypeTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.RawPathTimeBucketCountProjection;
import com.clickchecker.event.repository.projection.TimeBucketCountProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import com.clickchecker.eventrollup.entity.EventRollupWatermark;
import com.clickchecker.eventrollup.repository.EventHourlyRollupReadRepository;
import com.clickchecker.eventrollup.repository.EventRollupWatermarkRepository;
import com.clickchecker.route.service.RouteKeyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrendAnalyticsServiceTest {

    private final EventTrendNativeQueryRepository eventTrendNativeQueryRepository = mock(EventTrendNativeQueryRepository.class);
    private final EventHourlyRollupReadRepository eventHourlyRollupReadRepository = mock(EventHourlyRollupReadRepository.class);
    private final EventRollupWatermarkRepository eventRollupWatermarkRepository = mock(EventRollupWatermarkRepository.class);
    private final RouteKeyResolver routeKeyResolver = mock(RouteKeyResolver.class);
    private final CanonicalEventTypeResolver canonicalEventTypeResolver = mock(CanonicalEventTypeResolver.class);

    private final TrendAnalyticsService trendAnalyticsService =
            new TrendAnalyticsService(
                    eventTrendNativeQueryRepository,
                    eventHourlyRollupReadRepository,
                    eventRollupWatermarkRepository,
                    routeKeyResolver,
                    canonicalEventTypeResolver
            );

    @BeforeEach
    void setUp() {
        when(eventRollupWatermarkRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void countByRouteKeyTimeBucketBetween_aggregatesByResolvedRouteKey_withinEachBucket() {
        Instant from = Instant.parse("2026-03-01T10:00:00Z");
        Instant to = Instant.parse("2026-03-01T12:00:00Z");
        Instant bucket10 = Instant.parse("2026-03-01T10:00:00Z");
        Instant bucket11 = Instant.parse("2026-03-01T11:00:00Z");

        when(eventTrendNativeQueryRepository.countBucketedPathOccurredAtBetween(
                from, to, 1L, null, "click", TimeBucket.HOUR, "UTC"
        ))
                .thenReturn(List.of(
                        new RawPathTimeBucketCountProjection("/posts/1", bucket10, 5),
                        new RawPathTimeBucketCountProjection("/posts/2", bucket10, 4),
                        new RawPathTimeBucketCountProjection("/landing", bucket11, 3)
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

        when(eventTrendNativeQueryRepository.countBucketedEventTypeOccurredAtBetween(
                from, to, 1L, null, TimeBucket.HOUR, "UTC"
        ))
                .thenReturn(List.of(
                        new RawEventTypeTimeBucketCountProjection("button_click", bucket10, 5),
                        new RawEventTypeTimeBucketCountProjection("post_click", bucket10, 4),
                        new RawEventTypeTimeBucketCountProjection("page_view", bucket11, 3),
                        new RawEventTypeTimeBucketCountProjection("mystery_event", bucket11, 1)
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

        when(eventTrendNativeQueryRepository.countBucketedOccurredAtBetween(
                from, to, 1L, null, null, TimeBucket.DAY, "Asia/Seoul"
        ))
                .thenReturn(List.of(
                        new TimeBucketCountProjection(bucketStartKstDay1, 3)
                ));

        List<TimeBucketCountProjection> result =
                trendAnalyticsService.countByTimeBucketBetween(from, to, 1L, null, null, TimeBucket.DAY, "Asia/Seoul");

        assertThat(result).containsExactly(
                new TimeBucketCountProjection(bucketStartKstDay1, 3),
                new TimeBucketCountProjection(bucketStartKstDay2, 0)
        );
    }

    @Test
    void countByTimeBucketBetween_usesRollupForFullHoursAndRawForPartialsAndTail() {
        Instant from = Instant.parse("2026-03-01T12:30:00Z");
        Instant to = Instant.parse("2026-03-01T15:30:00Z");
        Instant watermark = Instant.parse("2026-03-01T16:00:00Z");

        when(eventRollupWatermarkRepository.findById(1L))
                .thenReturn(Optional.of(new EventRollupWatermark(1L, watermark)));

        when(eventHourlyRollupReadRepository.countHourlyBetween(
                Instant.parse("2026-03-01T13:00:00Z"),
                Instant.parse("2026-03-01T15:00:00Z"),
                1L,
                "click"
        ))
                .thenReturn(List.of(
                        new TimeBucketCountProjection(Instant.parse("2026-03-01T13:00:00Z"), 10),
                        new TimeBucketCountProjection(Instant.parse("2026-03-01T14:00:00Z"), 20)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyOccurredAtBetweenCreatedAfter(
                Instant.parse("2026-03-01T13:00:00Z"),
                Instant.parse("2026-03-01T15:00:00Z"),
                1L,
                "click",
                watermark
        ))
                .thenReturn(List.of(
                        new TimeBucketCountProjection(Instant.parse("2026-03-01T13:00:00Z"), 1),
                        new TimeBucketCountProjection(Instant.parse("2026-03-01T14:00:00Z"), 2)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyOccurredAtBetween(
                Instant.parse("2026-03-01T12:30:00Z"),
                Instant.parse("2026-03-01T13:00:00Z"),
                1L,
                "click"
        ))
                .thenReturn(List.of(
                        new TimeBucketCountProjection(Instant.parse("2026-03-01T12:00:00Z"), 3)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyOccurredAtBetween(
                Instant.parse("2026-03-01T15:00:00Z"),
                Instant.parse("2026-03-01T15:30:00Z"),
                1L,
                "click"
        ))
                .thenReturn(List.of(
                        new TimeBucketCountProjection(Instant.parse("2026-03-01T15:00:00Z"), 4)
                ));

        List<TimeBucketCountProjection> result =
                trendAnalyticsService.countByTimeBucketBetween(from, to, 1L, null, "click", TimeBucket.HOUR, "UTC");

        assertThat(result).containsExactly(
                new TimeBucketCountProjection(Instant.parse("2026-03-01T12:00:00Z"), 3),
                new TimeBucketCountProjection(Instant.parse("2026-03-01T13:00:00Z"), 11),
                new TimeBucketCountProjection(Instant.parse("2026-03-01T14:00:00Z"), 22),
                new TimeBucketCountProjection(Instant.parse("2026-03-01T15:00:00Z"), 4)
        );
    }

    @Test
    void countByRouteKeyTimeBucketBetween_usesRollupForFullHoursAndRawForPartialsAndTail() {
        Instant from = Instant.parse("2026-03-01T12:30:00Z");
        Instant to = Instant.parse("2026-03-01T15:30:00Z");
        Instant watermark = Instant.parse("2026-03-01T16:00:00Z");

        when(eventRollupWatermarkRepository.findById(1L))
                .thenReturn(Optional.of(new EventRollupWatermark(1L, watermark)));

        when(eventHourlyRollupReadRepository.countHourlyPathBetween(
                Instant.parse("2026-03-01T13:00:00Z"),
                Instant.parse("2026-03-01T15:00:00Z"),
                1L,
                "click"
        ))
                .thenReturn(List.of(
                        new RawPathTimeBucketCountProjection("/posts/1", Instant.parse("2026-03-01T13:00:00Z"), 5),
                        new RawPathTimeBucketCountProjection("/posts/2", Instant.parse("2026-03-01T14:00:00Z"), 4)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyPathOccurredAtBetweenCreatedAfter(
                Instant.parse("2026-03-01T13:00:00Z"),
                Instant.parse("2026-03-01T15:00:00Z"),
                1L,
                "click",
                watermark
        ))
                .thenReturn(List.of(
                        new RawPathTimeBucketCountProjection("/posts/2", Instant.parse("2026-03-01T13:00:00Z"), 1)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyPathOccurredAtBetween(
                Instant.parse("2026-03-01T12:30:00Z"),
                Instant.parse("2026-03-01T13:00:00Z"),
                1L,
                "click"
        ))
                .thenReturn(List.of(
                        new RawPathTimeBucketCountProjection("/landing", Instant.parse("2026-03-01T12:00:00Z"), 2)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyPathOccurredAtBetween(
                Instant.parse("2026-03-01T15:00:00Z"),
                Instant.parse("2026-03-01T15:30:00Z"),
                1L,
                "click"
        ))
                .thenReturn(List.of(
                        new RawPathTimeBucketCountProjection("/posts/1", Instant.parse("2026-03-01T15:00:00Z"), 3)
                ));

        when(routeKeyResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "/posts/1", "/posts/{id}",
                        "/posts/2", "/posts/{id}",
                        "/landing", "/landing"
                ));

        List<RouteTimeBucketItem> result =
                trendAnalyticsService.countByRouteKeyTimeBucketBetween(
                        from, to, 1L, null, "click", TimeBucket.HOUR, "UTC"
                );

        assertThat(result).containsExactly(
                new RouteTimeBucketItem("/landing", Instant.parse("2026-03-01T12:00:00Z"), 2),
                new RouteTimeBucketItem("/posts/{id}", Instant.parse("2026-03-01T12:00:00Z"), 0),
                new RouteTimeBucketItem("/landing", Instant.parse("2026-03-01T13:00:00Z"), 0),
                new RouteTimeBucketItem("/posts/{id}", Instant.parse("2026-03-01T13:00:00Z"), 6),
                new RouteTimeBucketItem("/landing", Instant.parse("2026-03-01T14:00:00Z"), 0),
                new RouteTimeBucketItem("/posts/{id}", Instant.parse("2026-03-01T14:00:00Z"), 4),
                new RouteTimeBucketItem("/landing", Instant.parse("2026-03-01T15:00:00Z"), 0),
                new RouteTimeBucketItem("/posts/{id}", Instant.parse("2026-03-01T15:00:00Z"), 3)
        );
    }

    @Test
    void countByCanonicalEventTypeTimeBucketBetween_usesRollupForFullHoursAndRawForPartialsAndTail() {
        Instant from = Instant.parse("2026-03-01T12:30:00Z");
        Instant to = Instant.parse("2026-03-01T15:30:00Z");
        Instant watermark = Instant.parse("2026-03-01T16:00:00Z");

        when(eventRollupWatermarkRepository.findById(1L))
                .thenReturn(Optional.of(new EventRollupWatermark(1L, watermark)));

        when(eventHourlyRollupReadRepository.countHourlyEventTypeBetween(
                Instant.parse("2026-03-01T13:00:00Z"),
                Instant.parse("2026-03-01T15:00:00Z"),
                1L
        ))
                .thenReturn(List.of(
                        new RawEventTypeTimeBucketCountProjection("button_click", Instant.parse("2026-03-01T13:00:00Z"), 5),
                        new RawEventTypeTimeBucketCountProjection("page_view", Instant.parse("2026-03-01T14:00:00Z"), 4)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyEventTypeOccurredAtBetweenCreatedAfter(
                Instant.parse("2026-03-01T13:00:00Z"),
                Instant.parse("2026-03-01T15:00:00Z"),
                1L,
                watermark
        ))
                .thenReturn(List.of(
                        new RawEventTypeTimeBucketCountProjection("post_click", Instant.parse("2026-03-01T13:00:00Z"), 1)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyEventTypeOccurredAtBetween(
                Instant.parse("2026-03-01T12:30:00Z"),
                Instant.parse("2026-03-01T13:00:00Z"),
                1L
        ))
                .thenReturn(List.of(
                        new RawEventTypeTimeBucketCountProjection("mystery_event", Instant.parse("2026-03-01T12:00:00Z"), 2)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyEventTypeOccurredAtBetween(
                Instant.parse("2026-03-01T15:00:00Z"),
                Instant.parse("2026-03-01T15:30:00Z"),
                1L
        ))
                .thenReturn(List.of(
                        new RawEventTypeTimeBucketCountProjection("page_view", Instant.parse("2026-03-01T15:00:00Z"), 3)
                ));

        when(canonicalEventTypeResolver.resolveAll(eq(1L), anyCollection()))
                .thenReturn(Map.of(
                        "button_click", "click",
                        "post_click", "click",
                        "page_view", "view",
                        "mystery_event", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE
                ));

        List<CanonicalEventTypeTimeBucketItem> result =
                trendAnalyticsService.countByCanonicalEventTypeTimeBucketBetween(
                        from, to, 1L, null, TimeBucket.HOUR, "UTC"
                );

        assertThat(result).containsExactly(
                new CanonicalEventTypeTimeBucketItem(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, Instant.parse("2026-03-01T12:00:00Z"), 2),
                new CanonicalEventTypeTimeBucketItem("click", Instant.parse("2026-03-01T12:00:00Z"), 0),
                new CanonicalEventTypeTimeBucketItem("view", Instant.parse("2026-03-01T12:00:00Z"), 0),
                new CanonicalEventTypeTimeBucketItem(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, Instant.parse("2026-03-01T13:00:00Z"), 0),
                new CanonicalEventTypeTimeBucketItem("click", Instant.parse("2026-03-01T13:00:00Z"), 6),
                new CanonicalEventTypeTimeBucketItem("view", Instant.parse("2026-03-01T13:00:00Z"), 0),
                new CanonicalEventTypeTimeBucketItem(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, Instant.parse("2026-03-01T14:00:00Z"), 0),
                new CanonicalEventTypeTimeBucketItem("click", Instant.parse("2026-03-01T14:00:00Z"), 0),
                new CanonicalEventTypeTimeBucketItem("view", Instant.parse("2026-03-01T14:00:00Z"), 4),
                new CanonicalEventTypeTimeBucketItem(CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, Instant.parse("2026-03-01T15:00:00Z"), 0),
                new CanonicalEventTypeTimeBucketItem("click", Instant.parse("2026-03-01T15:00:00Z"), 0),
                new CanonicalEventTypeTimeBucketItem("view", Instant.parse("2026-03-01T15:00:00Z"), 3)
        );
    }

    @Test
    void countByRouteKeyAndCanonicalEventTypeTimeBucketBetween_usesRollupForFullHoursAndRawForPartialsAndTail() {
        Instant from = Instant.parse("2026-03-01T12:30:00Z");
        Instant to = Instant.parse("2026-03-01T15:30:00Z");
        Instant watermark = Instant.parse("2026-03-01T16:00:00Z");

        when(eventRollupWatermarkRepository.findById(1L))
                .thenReturn(Optional.of(new EventRollupWatermark(1L, watermark)));

        when(eventHourlyRollupReadRepository.countHourlyPathEventTypeBetween(
                Instant.parse("2026-03-01T13:00:00Z"),
                Instant.parse("2026-03-01T15:00:00Z"),
                1L
        ))
                .thenReturn(List.of(
                        new RawPathEventTypeTimeBucketCountProjection("/posts/1", "button_click", Instant.parse("2026-03-01T13:00:00Z"), 5),
                        new RawPathEventTypeTimeBucketCountProjection("/landing", "page_view", Instant.parse("2026-03-01T14:00:00Z"), 4)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyPathEventTypeOccurredAtBetweenCreatedAfter(
                Instant.parse("2026-03-01T13:00:00Z"),
                Instant.parse("2026-03-01T15:00:00Z"),
                1L,
                watermark
        ))
                .thenReturn(List.of(
                        new RawPathEventTypeTimeBucketCountProjection("/posts/2", "post_click", Instant.parse("2026-03-01T13:00:00Z"), 1)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyPathEventTypeOccurredAtBetween(
                Instant.parse("2026-03-01T12:30:00Z"),
                Instant.parse("2026-03-01T13:00:00Z"),
                1L
        ))
                .thenReturn(List.of(
                        new RawPathEventTypeTimeBucketCountProjection("/landing", "mystery_event", Instant.parse("2026-03-01T12:00:00Z"), 2)
                ));

        when(eventTrendNativeQueryRepository.countUtcHourlyPathEventTypeOccurredAtBetween(
                Instant.parse("2026-03-01T15:00:00Z"),
                Instant.parse("2026-03-01T15:30:00Z"),
                1L
        ))
                .thenReturn(List.of(
                        new RawPathEventTypeTimeBucketCountProjection("/posts/1", "button_click", Instant.parse("2026-03-01T15:00:00Z"), 3)
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

        List<RouteEventTypeTimeBucketItem> result =
                trendAnalyticsService.countByRouteKeyAndCanonicalEventTypeTimeBucketBetween(
                        from, to, 1L, null, TimeBucket.HOUR, "UTC"
                );

        assertThat(result).containsExactly(
                new RouteEventTypeTimeBucketItem("/landing", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, Instant.parse("2026-03-01T12:00:00Z"), 2),
                new RouteEventTypeTimeBucketItem("/landing", "view", Instant.parse("2026-03-01T12:00:00Z"), 0),
                new RouteEventTypeTimeBucketItem("/posts/{id}", "click", Instant.parse("2026-03-01T12:00:00Z"), 0),
                new RouteEventTypeTimeBucketItem("/landing", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, Instant.parse("2026-03-01T13:00:00Z"), 0),
                new RouteEventTypeTimeBucketItem("/landing", "view", Instant.parse("2026-03-01T13:00:00Z"), 0),
                new RouteEventTypeTimeBucketItem("/posts/{id}", "click", Instant.parse("2026-03-01T13:00:00Z"), 6),
                new RouteEventTypeTimeBucketItem("/landing", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, Instant.parse("2026-03-01T14:00:00Z"), 0),
                new RouteEventTypeTimeBucketItem("/landing", "view", Instant.parse("2026-03-01T14:00:00Z"), 4),
                new RouteEventTypeTimeBucketItem("/posts/{id}", "click", Instant.parse("2026-03-01T14:00:00Z"), 0),
                new RouteEventTypeTimeBucketItem("/landing", CanonicalEventTypeResolver.UNMAPPED_EVENT_TYPE, Instant.parse("2026-03-01T15:00:00Z"), 0),
                new RouteEventTypeTimeBucketItem("/landing", "view", Instant.parse("2026-03-01T15:00:00Z"), 0),
                new RouteEventTypeTimeBucketItem("/posts/{id}", "click", Instant.parse("2026-03-01T15:00:00Z"), 3)
        );
    }

    @Test
    void countByRouteKeyTimeBucketBetween_fillsMissingDayBuckets_usingRequestedTimezone() {
        Instant from = Instant.parse("2026-03-01T15:00:00Z");
        Instant to = Instant.parse("2026-03-03T15:00:00Z");
        Instant bucketStartKstDay1 = Instant.parse("2026-03-01T15:00:00Z");
        Instant bucketStartKstDay2 = Instant.parse("2026-03-02T15:00:00Z");

        when(eventTrendNativeQueryRepository.countBucketedPathOccurredAtBetween(
                from, to, 1L, null, "click", TimeBucket.DAY, "Asia/Seoul"
        ))
                .thenReturn(List.of(
                        new RawPathTimeBucketCountProjection("/posts/1", bucketStartKstDay1, 2)
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

        when(eventTrendNativeQueryRepository.countBucketedEventTypeOccurredAtBetween(
                from, to, 1L, null, TimeBucket.DAY, "Asia/Seoul"
        ))
                .thenReturn(List.of(
                        new RawEventTypeTimeBucketCountProjection("button_click", bucketStartKstDay1, 2)
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

        when(eventTrendNativeQueryRepository.countBucketedPathEventTypeOccurredAtBetween(
                from, to, 1L, null, TimeBucket.HOUR, "UTC"
        ))
                .thenReturn(List.of(
                        new RawPathEventTypeTimeBucketCountProjection("/posts/1", "button_click", bucket10, 2),
                        new RawPathEventTypeTimeBucketCountProjection("/landing", "page_view", bucket11, 1)
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
