package com.clickchecker.eventtype.entity;

import com.clickchecker.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "event_type_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_type_mappings_org_raw_event_type",
                        columnNames = {"organization_id", "raw_event_type"}
                )
        }
)
public class EventTypeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "raw_event_type", nullable = false, length = 100)
    private String rawEventType;

    @Column(name = "canonical_event_type", nullable = false, length = 100)
    private String canonicalEventType;

    @Column(nullable = false)
    private boolean active;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private EventTypeMapping(
            Organization organization,
            String rawEventType,
            String canonicalEventType,
            Boolean active
    ) {
        this.organization = organization;
        this.rawEventType = rawEventType;
        this.canonicalEventType = canonicalEventType;
        this.active = active == null || active;
    }

    public void update(String rawEventType, String canonicalEventType) {
        this.rawEventType = rawEventType;
        this.canonicalEventType = canonicalEventType;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
