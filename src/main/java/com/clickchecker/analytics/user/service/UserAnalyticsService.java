package com.clickchecker.analytics.user.service;

import com.clickchecker.analytics.user.controller.response.UserAnalyticsOverviewResponse;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.IdentifiedUserEventCountProjection;
import com.clickchecker.event.repository.projection.IdentifiedUserFirstSeenProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        long totalEvents = identifiedUserEvents.stream()
                .mapToLong(IdentifiedUserEventCountProjection::eventCount)
                .sum();

        Double avgEventsPerIdentifiedUser = identifiedUsers == 0
                ? null
                : totalEvents / (double) identifiedUsers;

        Map<Long, Instant> firstSeenByUserId = eventQueryRepository.findIdentifiedUserFirstSeen(
                        organizationId,
                        externalUserId
                ).stream()
                .collect(Collectors.toMap(
                        IdentifiedUserFirstSeenProjection::eventUserId,
                        IdentifiedUserFirstSeenProjection::firstSeenAt,
                        (left, right) -> left
                ));

        long newUsers = identifiedUserEvents.stream()
                .map(IdentifiedUserEventCountProjection::eventUserId)
                .map(firstSeenByUserId::get)
                .filter(firstSeen -> firstSeen != null && !firstSeen.isBefore(from) && firstSeen.isBefore(to))
                .count();

        long returningUsers = identifiedUsers - newUsers;

        return new UserAnalyticsOverviewResponse(
                organizationId,
                externalUserId,
                from,
                to,
                identifiedUsers,
                newUsers,
                returningUsers,
                avgEventsPerIdentifiedUser
        );
    }
}
