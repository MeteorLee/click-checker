package com.clickchecker.event.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "events",
        indexes = {
                @Index(name = "idx_events_service_received", columnList = "service_id, received_at"),
                @Index(name = "idx_events_service_target", columnList = "service_id, target_type, target_id"),
                @Index(name = "idx_events_service_action", columnList = "service_id, action_type")
        }
)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어느 고객 서비스에서 온 이벤트인지 (멀티테넌트의 씨앗)
    @Column(name = "service_id", nullable = false, length = 50)
    private String serviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected Event() {}

    public Event(String serviceId,
                 TargetType targetType,
                 String targetId,
                 ActionType actionType,
                 Instant occurredAt,
                 Instant receivedAt) {
        this.serviceId = serviceId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.actionType = actionType;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getServiceId() { return serviceId; }
    public TargetType getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public ActionType getActionType() { return actionType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
