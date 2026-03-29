package com.clickchecker.analytics.user.service;

import com.clickchecker.analytics.user.controller.response.UserAnalyticsOverviewResponse;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.IdentifiedUserEventCountProjection;
import com.clickchecker.event.repository.projection.IdentifiedUserFirstSeenProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class UserAnalyticsService {

    private final EventQueryRepository eventQueryRepository;

    @Transactional(readOnly = true)
    public UserAnalyticsOverviewResponse getOverview(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId
    ) {
        var identifiedUserEvents = eventQueryRepository.countIdentifiedUserEventBetween(
                from,
                to,
                organizationId,
                externalUserId
        );

        long identifiedUsers = identifiedUserEvents.size();
        long identifiedEvents = identifiedUserEvents.stream()
                .mapToLong(IdentifiedUserEventCountProjection::eventCount)
                .sum();
        long totalEvents = eventQueryRepository.countBetween(from, to, organizationId, externalUserId, null);
        long anonymousEvents = totalEvents - identifiedEvents;

        Double avgEventsPerIdentifiedUser = identifiedUsers == 0
                ? null
                : identifiedEvents / (double) identifiedUsers;

        Map<Long, Instant> firstSeenByUserId = eventQueryRepository.findIdentifiedUserFirstSeen(
                        organizationId,
                        externalUserId
                ).stream()
                .collect(Collectors.toMap(
                        IdentifiedUserFirstSeenProjection::eventUserId,
                        IdentifiedUserFirstSeenProjection::firstSeenAt,
                        (left, right) -> left
                ));

        List<IdentifiedUserEventCountProjection> newUserEventCounts = identifiedUserEvents.stream()
                .filter(userEvent ->
                        isNewUser(firstSeenByUserId.get(userEvent.eventUserId()), from, to)
                )
                .toList();

        long newUsers = newUserEventCounts.size();

        long returningUsers = identifiedUsers - newUsers;
        long newUserEvents = newUserEventCounts.stream()
                .mapToLong(IdentifiedUserEventCountProjection::eventCount)
                .sum();
        long returningUserEvents = identifiedEvents - newUserEvents;

        return new UserAnalyticsOverviewResponse(
                organizationId,
                externalUserId,
                from,
                to,
                totalEvents,
                identifiedEvents,
                anonymousEvents,
                identifiedUsers,
                newUsers,
                returningUsers,
                newUserEvents,
                returningUserEvents,
                avgEventsPerIdentifiedUser
        );
    }

    private boolean isNewUser(Instant firstSeen, Instant from, Instant to) {
        return firstSeen != null && !firstSeen.isBefore(from) && firstSeen.isBefore(to);
    }
}
