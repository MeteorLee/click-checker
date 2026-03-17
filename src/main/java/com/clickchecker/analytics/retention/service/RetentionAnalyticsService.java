package com.clickchecker.analytics.retention.service;

import com.clickchecker.analytics.retention.controller.response.DailyRetentionItem;
import com.clickchecker.analytics.retention.controller.response.DailyRetentionResponse;
import com.clickchecker.analytics.retention.controller.response.RetentionMatrixResponse;
import com.clickchecker.analytics.retention.controller.response.RetentionMatrixRow;
import com.clickchecker.analytics.retention.controller.response.RetentionMatrixValue;
import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.projection.IdentifiedUserFirstSeenProjection;
import com.clickchecker.event.repository.projection.IdentifiedUserOccurredAtProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class RetentionAnalyticsService {

    private final EventQueryRepository eventQueryRepository;

    @Transactional(readOnly = true)
    public DailyRetentionResponse getDailyRetention(
            Instant from,
            Instant to,
            ZoneId zoneId,
            Long organizationId,
            String externalUserId
    ) {
        RetentionContext context = buildContext(from, to, zoneId, organizationId, externalUserId, 30);

        List<DailyRetentionItem> items = context.cohortUserIdsByDate().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> buildItem(entry.getKey(), entry.getValue(), context.activeDatesByUserId()))
                .toList();

        return new DailyRetentionResponse(
                organizationId,
                externalUserId,
                from,
                to,
                zoneId.getId(),
                items
        );
    }

    @Transactional(readOnly = true)
    public RetentionMatrixResponse getRetentionMatrix(
            Instant from,
            Instant to,
            ZoneId zoneId,
            Long organizationId,
            String externalUserId,
            List<Integer> days,
            int minCohortUsers
    ) {
        int maxDay = days.stream().mapToInt(Integer::intValue).max().orElse(30);
        RetentionContext context = buildContext(from, to, zoneId, organizationId, externalUserId, maxDay);

        List<RetentionMatrixRow> items = context.cohortUserIdsByDate().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue().size() >= minCohortUsers)
                .map(entry -> buildMatrixRow(entry.getKey(), entry.getValue(), context.activeDatesByUserId(), days))
                .toList();

        return new RetentionMatrixResponse(
                organizationId,
                externalUserId,
                from,
                to,
                zoneId.getId(),
                days,
                items
        );
    }

    private DailyRetentionItem buildItem(
            LocalDate cohortDate,
            List<Long> cohortUserIds,
            Map<Long, Set<LocalDate>> activeDatesByUserId
    ) {
        long cohortUsers = cohortUserIds.size();
        long day1Users = retainedUsers(cohortUserIds, activeDatesByUserId, cohortDate.plusDays(1));
        long day7Users = retainedUsers(cohortUserIds, activeDatesByUserId, cohortDate.plusDays(7));
        long day30Users = retainedUsers(cohortUserIds, activeDatesByUserId, cohortDate.plusDays(30));

        return new DailyRetentionItem(
                cohortDate,
                cohortUsers,
                day1Users,
                retentionRate(day1Users, cohortUsers),
                day7Users,
                retentionRate(day7Users, cohortUsers),
                day30Users,
                retentionRate(day30Users, cohortUsers)
        );
    }

    private RetentionMatrixRow buildMatrixRow(
            LocalDate cohortDate,
            List<Long> cohortUserIds,
            Map<Long, Set<LocalDate>> activeDatesByUserId,
            List<Integer> days
    ) {
        long cohortUsers = cohortUserIds.size();
        List<RetentionMatrixValue> values = days.stream()
                .map(day -> {
                    long retainedUsers = retainedUsers(cohortUserIds, activeDatesByUserId, cohortDate.plusDays(day));
                    return new RetentionMatrixValue(
                            day,
                            retainedUsers,
                            retentionRate(retainedUsers, cohortUsers)
                    );
                })
                .toList();

        return new RetentionMatrixRow(
                cohortDate,
                cohortUsers,
                values
        );
    }

    private RetentionContext buildContext(
            Instant from,
            Instant to,
            ZoneId zoneId,
            Long organizationId,
            String externalUserId,
            int maxRetentionDay
    ) {
        List<IdentifiedUserFirstSeenProjection> firstSeenRows = eventQueryRepository.findIdentifiedUserFirstSeen(
                organizationId,
                externalUserId
        );

        Instant activityTo = to.plusSeconds((long) (maxRetentionDay + 1) * 24 * 60 * 60);
        Map<Long, Set<LocalDate>> activeDatesByUserId = eventQueryRepository.findIdentifiedUserOccurredAtBetween(
                        from,
                        activityTo,
                        organizationId,
                        externalUserId
                ).stream()
                .collect(Collectors.groupingBy(
                        IdentifiedUserOccurredAtProjection::eventUserId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                item -> item.occurredAt().atZone(zoneId).toLocalDate(),
                                Collectors.toSet()
                        )
                ));

        Map<LocalDate, List<Long>> cohortUserIdsByDate = new LinkedHashMap<>();
        for (IdentifiedUserFirstSeenProjection firstSeenRow : firstSeenRows) {
            Instant firstSeenAt = firstSeenRow.firstSeenAt();
            if (firstSeenAt.isBefore(from) || !firstSeenAt.isBefore(to)) {
                continue;
            }

            LocalDate cohortDate = firstSeenAt.atZone(zoneId).toLocalDate();
            cohortUserIdsByDate.computeIfAbsent(cohortDate, ignored -> new ArrayList<>())
                    .add(firstSeenRow.eventUserId());
        }

        return new RetentionContext(activeDatesByUserId, cohortUserIdsByDate);
    }

    private long retainedUsers(
            List<Long> cohortUserIds,
            Map<Long, Set<LocalDate>> activeDatesByUserId,
            LocalDate targetDate
    ) {
        return cohortUserIds.stream()
                .filter(userId -> activeDatesByUserId.getOrDefault(userId, Set.of()).contains(targetDate))
                .count();
    }

    private Double retentionRate(long retainedUsers, long cohortUsers) {
        if (cohortUsers == 0) {
            return null;
        }
        return retainedUsers / (double) cohortUsers;
    }

    private record RetentionContext(
            Map<Long, Set<LocalDate>> activeDatesByUserId,
            Map<LocalDate, List<Long>> cohortUserIdsByDate
    ) {
    }
}
