package com.fandrops.notification.infrastructure.jpa;

import com.fandrops.notification.domain.OutboxEvent;
import com.fandrops.notification.domain.OutboxStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_event_id")
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventJpaEntity() {}

    public static OutboxEventJpaEntity from(OutboxEvent event) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.id = event.getId();
        entity.eventType = event.getEventType();
        entity.resourceId = event.getResourceId();
        entity.payload = event.getPayload();
        entity.status = event.getStatus();
        entity.retryCount = event.getRetryCount();
        entity.createdAt = event.getCreatedAt();
        entity.publishedAt = event.getPublishedAt();
        return entity;
    }

    public OutboxEvent toDomain() {
        return OutboxEvent.builder()
                .id(id)
                .eventType(eventType)
                .resourceId(resourceId)
                .payload(payload)
                .status(status)
                .retryCount(retryCount)
                .createdAt(createdAt)
                .publishedAt(publishedAt)
                .build();
    }

    public Long getId() { return id; }
}