package com.clickchecker.event.service;

import com.clickchecker.event.repository.EventQueryRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.event.repository.dto.PathCountDto;
import com.clickchecker.event.repository.dto.TimeBucket;
import com.clickchecker.event.repository.dto.TimeBucketCountDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class EventQueryService {

    private final EventRepository eventRepository;
    private final EventQueryRepository eventQueryRepository;

    @Transactional(readOnly = true)
    public long countByEventType(String eventType) {
        return eventRepository.countByEventType(eventType);
    }

    @Transactional(readOnly = true)
    public List<PathCountDto> countByPathBetween(
            LocalDateTime from,
            LocalDateTime to,
            Long organizationId,
            String externalUserId,
            String eventType,
            int top
    ) {
        return eventQueryRepository.countByPathBetween(from, to, organizationId, externalUserId, eventType, top);
    }

    @Transactional(readOnly = true)
    public List<TimeBucketCountDto> countByTimeBucketBetween(
            LocalDateTime from,
            LocalDateTime to,
            Long organizationId,
            String externalUserId,
            String eventType,
            TimeBucket bucket
    ) {
        return eventQueryRepository.countByTimeBucketBetween(
                from,
                to,
                organizationId,
                externalUserId,
                eventType,
                bucket
        );
    }
}
