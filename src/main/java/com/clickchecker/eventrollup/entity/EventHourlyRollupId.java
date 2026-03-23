package com.clickchecker.eventrollup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class EventHourlyRollupId implements Serializable {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    public EventHourlyRollupId(
            Long organizationId,
            Instant bucketStart,
            String path,
            String eventType
    ) {
        this.organizationId = organizationId;
        this.bucketStart = bucketStart;
        this.path = path;
        this.eventType = eventType;
    }
}
