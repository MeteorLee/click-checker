package com.clickchecker.analytics.funnel.service;

import com.clickchecker.analytics.funnel.controller.response.FunnelReportResponse;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.IdentifiedUserEventTypeOccurredAtProjection;
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
    private final FunnelAnalyticsService funnelAnalyticsService =
            new FunnelAnalyticsService(eventQueryRepository, canonicalEventTypeResolver);

    @Test
    void report_countsSequentialSteps_usingSevenDayWindowAndSameTimestampRule() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-08T00:00:00Z");

        when(eventQueryRepository.findIdentifiedUserEventTypeOccurredAtBetween(
                from,
                Instant.parse("2026-03-15T00:00:00Z"),
                1L,
                null
        )).thenReturn(List.of(
                new IdentifiedUserEventTypeOccurredAtProjection(101L, "signup_button_click", Instant.parse("2026-03-07T10:00:00Z")),
                new IdentifiedUserEventTypeOccurredAtProjection(101L, "verify_email", Instant.parse("2026-03-07T10:00:00Z")),
                new IdentifiedUserEventTypeOccurredAtProjection(101L, "purchase_complete", Instant.parse("2026-03-10T09:00:00Z")),
                new IdentifiedUserEventTypeOccurredAtProjection(102L, "signup_button_click", Instant.parse("2026-03-07T12:00:00Z")),
                new IdentifiedUserEventTypeOccurredAtProjection(102L, "purchase_complete", Instant.parse("2026-03-07T13:00:00Z")),
                new IdentifiedUserEventTypeOccurredAtProjection(103L, "signup_button_click", Instant.parse("2026-03-07T14:00:00Z")),
                new IdentifiedUserEventTypeOccurredAtProjection(103L, "verify_email", Instant.parse("2026-03-16T00:00:00Z"))
        ));

        when(canonicalEventTypeResolver.resolve(1L, "signup_button_click")).thenReturn("SIGN_UP");
        when(canonicalEventTypeResolver.resolve(1L, "verify_email")).thenReturn("VERIFY_EMAIL");
        when(canonicalEventTypeResolver.resolve(1L, "purchase_complete")).thenReturn("PURCHASE");

        FunnelReportResponse result = funnelAnalyticsService.report(
                from,
                to,
                1L,
                null,
                List.of("SIGN_UP", "VERIFY_EMAIL", "PURCHASE")
        );

        assertThat(result.conversionWindow()).isEqualTo("7d");
        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).users()).isEqualTo(3);
        assertThat(result.items().get(0).conversionRateFromFirstStep()).isEqualTo(1.0);
        assertThat(result.items().get(0).previousStepUsers()).isNull();
        assertThat(result.items().get(0).conversionRateFromPreviousStep()).isNull();
        assertThat(result.items().get(0).dropOffUsersFromPreviousStep()).isNull();
        assertThat(result.items().get(1).users()).isEqualTo(1);
        assertThat(result.items().get(1).conversionRateFromFirstStep()).isEqualTo(1.0 / 3.0);
        assertThat(result.items().get(1).previousStepUsers()).isEqualTo(3);
        assertThat(result.items().get(1).conversionRateFromPreviousStep()).isEqualTo(1.0 / 3.0);
        assertThat(result.items().get(1).dropOffUsersFromPreviousStep()).isEqualTo(2);
        assertThat(result.items().get(2).users()).isEqualTo(1);
        assertThat(result.items().get(2).conversionRateFromFirstStep()).isEqualTo(1.0 / 3.0);
        assertThat(result.items().get(2).previousStepUsers()).isEqualTo(1);
        assertThat(result.items().get(2).conversionRateFromPreviousStep()).isEqualTo(1.0);
        assertThat(result.items().get(2).dropOffUsersFromPreviousStep()).isEqualTo(0);
    }
}
