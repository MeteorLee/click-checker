package com.clickchecker.event.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Lob
    private String payload;

    public Event(String eventType, LocalDateTime occurredAt, String payload) {
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.payload = payload;
    }
}
