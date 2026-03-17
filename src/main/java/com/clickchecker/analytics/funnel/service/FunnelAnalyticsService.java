package com.clickchecker.analytics.funnel.service;

import com.clickchecker.analytics.funnel.controller.response.FunnelReportResponse;
import com.clickchecker.analytics.funnel.controller.response.FunnelStepResult;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.IdentifiedUserEventTypeOccurredAtProjection;
import com.clickchecker.eventtype.service.CanonicalEventTypeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class FunnelAnalyticsService {

    private static final Duration DEFAULT_CONVERSION_WINDOW = Duration.ofDays(7);
    private static final String DEFAULT_CONVERSION_WINDOW_LABEL = "7d";

    private final EventQueryRepository eventQueryRepository;
    private final CanonicalEventTypeResolver canonicalEventTypeResolver;

    @Transactional(readOnly = true)
    public FunnelReportResponse report(
            Instant from,
            Instant to,
            Long organizationId,
            String externalUserId,
            List<String> requestedSteps
    ) {
        List<String> steps = requestedSteps.stream()
                .map(String::trim)
                .toList();

        Instant queryTo = to.plus(DEFAULT_CONVERSION_WINDOW);
        List<UserEvent> userEvents = eventQueryRepository.findIdentifiedUserEventTypeOccurredAtBetween(
                        from,
                        queryTo,
                        organizationId,
                        externalUserId
                ).stream()
                .map(item -> new UserEvent(
                        item.eventUserId(),
                        canonicalEventTypeResolver.resolve(organizationId, item.rawEventType()),
                        item.occurredAt()
                ))
                .toList();

        Map<Long, List<UserEvent>> eventsByUser = new LinkedHashMap<>();
        for (UserEvent userEvent : userEvents) {
            eventsByUser.computeIfAbsent(userEvent.eventUserId(), ignored -> new ArrayList<>())
                    .add(userEvent);
        }

        long[] stepUsers = new long[steps.size()];
        for (List<UserEvent> events : eventsByUser.values()) {
            int recognizedSteps = recognizeStepCount(events, steps, from, to);
            for (int i = 0; i < recognizedSteps; i++) {
                stepUsers[i]++;
            }
        }

        long firstStepUsers = stepUsers.length == 0 ? 0 : stepUsers[0];
        List<FunnelStepResult> items = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            Long previousStepUsers = i == 0 ? null : stepUsers[i - 1];
            items.add(new FunnelStepResult(
                    i + 1,
                    steps.get(i),
                    stepUsers[i],
                    conversionRateFromFirstStep(stepUsers[i], firstStepUsers),
                    previousStepUsers,
                    conversionRateFromPreviousStep(stepUsers[i], previousStepUsers),
                    dropOffUsersFromPreviousStep(stepUsers[i], previousStepUsers)
            ));
        }

        return new FunnelReportResponse(
                organizationId,
                externalUserId,
                from,
                to,
                steps,
                DEFAULT_CONVERSION_WINDOW_LABEL,
                items
        );
    }

    private int recognizeStepCount(
            List<UserEvent> events,
            List<String> steps,
            Instant from,
            Instant to
    ) {
        UserEvent anchor = findFirstMatching(events, steps.getFirst(), from, to, false);
        if (anchor == null) {
            return 0;
        }

        Instant windowEnd = anchor.occurredAt().plus(DEFAULT_CONVERSION_WINDOW);
        Instant previousStepTime = anchor.occurredAt();
        int recognizedSteps = 1;

        for (int i = 1; i < steps.size(); i++) {
            UserEvent nextStep = findFirstMatching(events, steps.get(i), previousStepTime, windowEnd, true);
            if (nextStep == null) {
                break;
            }
            recognizedSteps++;
            previousStepTime = nextStep.occurredAt();
        }

        return recognizedSteps;
    }

    private UserEvent findFirstMatching(
            List<UserEvent> events,
            String expectedCanonicalEventType,
            Instant from,
            Instant to,
            boolean inclusiveUpperBound
    ) {
        for (UserEvent event : events) {
            if (event.occurredAt().isBefore(from)) {
                continue;
            }
            if (isAfterUpperBound(event.occurredAt(), to, inclusiveUpperBound)) {
                return null;
            }
            if (event.canonicalEventType().equals(expectedCanonicalEventType)) {
                return event;
            }
        }

        return null;
    }

    private boolean isAfterUpperBound(Instant value, Instant upperBound, boolean inclusiveUpperBound) {
        return inclusiveUpperBound ? value.isAfter(upperBound) : !value.isBefore(upperBound);
    }

    private Double conversionRateFromFirstStep(long stepUsers, long firstStepUsers) {
        if (firstStepUsers == 0) {
            return null;
        }
        return stepUsers / (double) firstStepUsers;
    }

    private Double conversionRateFromPreviousStep(long stepUsers, Long previousStepUsers) {
        if (previousStepUsers == null || previousStepUsers == 0) {
            return null;
        }
        return stepUsers / (double) previousStepUsers;
    }

    private Long dropOffUsersFromPreviousStep(long stepUsers, Long previousStepUsers) {
        if (previousStepUsers == null) {
            return null;
        }
        return previousStepUsers - stepUsers;
    }

    private record UserEvent(
            Long eventUserId,
            String canonicalEventType,
            Instant occurredAt
    ) {
    }
}
