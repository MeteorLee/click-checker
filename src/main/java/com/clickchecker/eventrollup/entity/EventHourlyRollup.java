package com.clickchecker.eventrollup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "event_hourly_rollups")
public class EventHourlyRollup {

    @EmbeddedId
    private EventHourlyRollupId id;

    @Column(nullable = false)
    private long eventCount;

    @Column(nullable = false)
    private long identifiedEventCount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public EventHourlyRollup(
            EventHourlyRollupId id,
            long eventCount,
            long identifiedEventCount
    ) {
        this.id = id;
        this.eventCount = eventCount;
        this.identifiedEventCount = identifiedEventCount;
    }

    public void overwriteCounts(long eventCount, long identifiedEventCount) {
        this.eventCount = eventCount;
        this.identifiedEventCount = identifiedEventCount;
    }
}
