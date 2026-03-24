package com.clickchecker.eventrollup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
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
@Table(name = "event_rollup_watermarks")
public class EventRollupWatermark {

    @Id
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "processed_created_at")
    private Instant processedCreatedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public EventRollupWatermark(Long organizationId, Instant processedCreatedAt) {
        this.organizationId = organizationId;
        this.processedCreatedAt = processedCreatedAt;
    }

    public void updateProcessedCreatedAt(Instant processedCreatedAt) {
        this.processedCreatedAt = processedCreatedAt;
    }
}
