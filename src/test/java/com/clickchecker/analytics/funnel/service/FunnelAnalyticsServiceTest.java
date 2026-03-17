package com.clickchecker.analytics.funnel.service;

import com.clickchecker.analytics.funnel.controller.request.FunnelStepRequest;
import com.clickchecker.analytics.funnel.controller.response.FunnelReportResponse;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.IdentifiedUserEventStepOccurredAtProjection;
import com.clickchecker.route.service.RouteKeyResolver;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FunnelAnalyticsServiceTest {

    private final EventQueryRepository eventQueryRepository = mock(EventQueryRepository.class);
    private final CanonicalEventTypeResolver canonicalEventTypeResolver = mock(CanonicalEventTypeResolver.class);
    private final RouteKeyResolver routeKeyResolver = mock(RouteKeyResolver.class);
    private final FunnelAnalyticsService funnelAnalyticsService =
            new FunnelAnalyticsService(eventQueryRepository, canonicalEventTypeResolver, routeKeyResolver);

    @Test
    void report_countsSequentialSteps_usingSevenDayWindowAndOptionalRouteKeyRule() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-08T00:00:00Z");

        when(eventQueryRepository.findIdentifiedUserEventStepOccurredAtBetween(
                from,
                Instant.parse("2026-03-15T00:00:00Z"),
                1L,
                null
        )).thenReturn(List.of(
                new IdentifiedUserEventStepOccurredAtProjection(101L, "page_view", "/pricing", Instant.parse("2026-03-07T09:00:00Z")),
                new IdentifiedUserEventStepOccurredAtProjection(101L, "signup_button_click", "/signup", Instant.parse("2026-03-07T10:00:00Z")),
                new IdentifiedUserEventStepOccurredAtProjection(101L, "purchase_complete", "/purchase", Instant.parse("2026-03-10T09:00:00Z")),
                new IdentifiedUserEventStepOccurredAtProjection(102L, "page_view", "/home", Instant.parse("2026-03-07T08:00:00Z")),
                new IdentifiedUserEventStepOccurredAtProjection(102L, "signup_button_click", "/signup", Instant.parse("2026-03-07T12:00:00Z")),
                new IdentifiedUserEventStepOccurredAtProjection(102L, "purchase_complete", "/purchase", Instant.parse("2026-03-07T13:00:00Z")),
                new IdentifiedUserEventStepOccurredAtProjection(103L, "page_view", "/pricing", Instant.parse("2026-03-07T14:00:00Z")),
                new IdentifiedUserEventStepOccurredAtProjection(103L, "purchase_complete", "/purchase", Instant.parse("2026-03-16T00:00:00Z"))
        ));

        when(canonicalEventTypeResolver.resolve(1L, "page_view")).thenReturn("PAGE_VIEW");
        when(canonicalEventTypeResolver.resolve(1L, "signup_button_click")).thenReturn("SIGN_UP");
        when(canonicalEventTypeResolver.resolve(1L, "purchase_complete")).thenReturn("PURCHASE");
        when(routeKeyResolver.resolve(1L, "/pricing")).thenReturn("/pricing");
        when(routeKeyResolver.resolve(1L, "/home")).thenReturn("/home");
        when(routeKeyResolver.resolve(1L, "/signup")).thenReturn("/signup");
        when(routeKeyResolver.resolve(1L, "/purchase")).thenReturn("/purchase");

        FunnelReportResponse result = funnelAnalyticsService.report(
                from,
                to,
                1L,
                null,
                List.of(
                        new FunnelStepRequest("PAGE_VIEW", "/pricing"),
                        new FunnelStepRequest("SIGN_UP", null),
                        new FunnelStepRequest("PURCHASE", null)
                )
        );

        assertThat(result.conversionWindow()).isEqualTo("7d");
        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).step().canonicalEventType()).isEqualTo("PAGE_VIEW");
        assertThat(result.items().get(0).step().routeKey()).isEqualTo("/pricing");
        assertThat(result.items().get(0).users()).isEqualTo(2);
        assertThat(result.items().get(0).conversionRateFromFirstStep()).isEqualTo(1.0);
        assertThat(result.items().get(0).previousStepUsers()).isNull();
        assertThat(result.items().get(0).conversionRateFromPreviousStep()).isNull();
        assertThat(result.items().get(0).dropOffUsersFromPreviousStep()).isNull();
        assertThat(result.items().get(1).users()).isEqualTo(1);
        assertThat(result.items().get(1).conversionRateFromFirstStep()).isEqualTo(0.5);
        assertThat(result.items().get(1).previousStepUsers()).isEqualTo(2);
        assertThat(result.items().get(1).conversionRateFromPreviousStep()).isEqualTo(0.5);
        assertThat(result.items().get(1).dropOffUsersFromPreviousStep()).isEqualTo(1);
        assertThat(result.items().get(2).users()).isEqualTo(1);
        assertThat(result.items().get(2).conversionRateFromFirstStep()).isEqualTo(0.5);
        assertThat(result.items().get(2).previousStepUsers()).isEqualTo(1);
        assertThat(result.items().get(2).conversionRateFromPreviousStep()).isEqualTo(1.0);
        assertThat(result.items().get(2).dropOffUsersFromPreviousStep()).isEqualTo(0);
    }
}
