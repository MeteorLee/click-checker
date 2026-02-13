package com.clickchecker.event.entity;

import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_user_id")
    private EventUser eventUser;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Lob
    private String payload;

    @Builder
    public Event(
            String eventType,
            String path,
            Organization organization,
            EventUser eventUser,
            LocalDateTime occurredAt,
            String payload
    ) {
        this.eventType = eventType;
        this.path = path;
        this.organization = organization;
        this.eventUser = eventUser;
        this.occurredAt = occurredAt != null ? occurredAt : LocalDateTime.now();
        this.payload = payload;
    }
}
