package com.clickchecker.event.entity;

import com.clickchecker.organization.entity.Organization;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType;

    @Column(length = 512)
    private String path;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Lob
    private String payload;

    @Builder
    public Event(String eventType, String path, Organization organization, LocalDateTime occurredAt, String payload) {
        this.eventType = eventType;
        this.path = path;
        this.organization = organization;
        this.occurredAt = occurredAt != null ? occurredAt : LocalDateTime.now();
        this.payload = payload;
    }
}
