package com.clickchecker.analytics.user.service;

import com.clickchecker.analytics.user.controller.response.UserAnalyticsOverviewResponse;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.IdentifiedUserEventCountProjection;
import com.clickchecker.event.repository.projection.IdentifiedUserFirstSeenProjection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAnalyticsServiceTest {

    private final EventQueryRepository eventQueryRepository = mock(EventQueryRepository.class);
    private final UserAnalyticsService userAnalyticsService = new UserAnalyticsService(eventQueryRepository);

    @Test
    void getOverview_calculatesIdentifiedNewReturningAndAverage() {
        Instant from = Instant.parse("2026-03-10T00:00:00Z");
        Instant to = Instant.parse("2026-03-17T00:00:00Z");

        when(eventQueryRepository.countIdentifiedUserEventBetween(from, to, 1L, null))
                .thenReturn(List.of(
                        new IdentifiedUserEventCountProjection(101L, 5),
                        new IdentifiedUserEventCountProjection(102L, 1),
                        new IdentifiedUserEventCountProjection(103L, 3)
                ));

        when(eventQueryRepository.findIdentifiedUserFirstSeen(1L, null))
                .thenReturn(List.of(
                        new IdentifiedUserFirstSeenProjection(101L, Instant.parse("2026-03-12T10:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(102L, Instant.parse("2026-03-01T09:00:00Z")),
                        new IdentifiedUserFirstSeenProjection(103L, Instant.parse("2026-03-10T00:00:00Z"))
                ));

        UserAnalyticsOverviewResponse result =
                userAnalyticsService.getOverview(from, to, 1L, null);

        assertThat(result.identifiedUsers()).isEqualTo(3);
        assertThat(result.newUsers()).isEqualTo(2);
        assertThat(result.returningUsers()).isEqualTo(1);
        assertThat(result.avgEventsPerIdentifiedUser()).isEqualTo(3.0);
    }
}
