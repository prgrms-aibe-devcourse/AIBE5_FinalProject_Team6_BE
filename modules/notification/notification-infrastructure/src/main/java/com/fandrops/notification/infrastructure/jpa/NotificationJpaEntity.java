package com.fandrops.notification.infrastructure.jpa;

import com.fandrops.notification.domain.Notification;
import com.fandrops.notification.domain.NotificationType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification")
public class NotificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "fan_id", nullable = false)
    private Long fanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected NotificationJpaEntity() {}

    public static NotificationJpaEntity from(Notification notification) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.id = notification.getId();
        entity.fanId = notification.getFanId();
        entity.type = notification.getType();
        entity.targetId = notification.getTargetId();
        entity.message = notification.getMessage();
        entity.read = notification.isRead();
        entity.sentAt = notification.getSentAt();
        return entity;
    }

    public Notification toDomain() {
        return Notification.builder()
                .id(id)
                .fanId(fanId)
                .type(type)
                .targetId(targetId)
                .message(message)
                .read(read)
                .sentAt(sentAt)
                .build();
    }

    public Long getId() { return id; }
}