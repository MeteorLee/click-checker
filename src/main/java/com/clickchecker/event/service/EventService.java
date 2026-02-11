package com.clickchecker.event.service;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class EventService {

    private final EventRepository eventRepository;

    @Transactional
    public Long create(String eventType, LocalDateTime occurredAt, String payload) {
        // 최소 방어
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now(); // 임시로 서버시간
        }

        Event event = new Event(eventType, occurredAt, payload);
        return eventRepository.save(event).getId();
    }

    @Transactional(readOnly = true)
    public long countByEventType(String eventType) {
        return eventRepository.countByEventType(eventType);
    }
}
