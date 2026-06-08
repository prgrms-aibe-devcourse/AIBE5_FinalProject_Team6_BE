package com.fandrops.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fandrops.notification.domain.Notification;
import java.time.Instant;

public class NotificationResult {

    private final Long id;
    private final String type;
    private final String message;
    private final boolean isRead;
    private final Instant sentAt;
    private final Long targetId;

    private NotificationResult(Long id, String type, String message, boolean isRead,
                               Instant sentAt, Long targetId) {
        this.id = id;
        this.type = type;
        this.message = message;
        this.isRead = isRead;
        this.sentAt = sentAt;
        this.targetId = targetId;
    }

    public static NotificationResult from(Notification notification) {
        return new NotificationResult(
                notification.getId(),
                notification.getType().name(),
                notification.getMessage(),
                notification.isRead(),
                notification.getSentAt(),
                notification.getTargetId()
        );
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    @JsonProperty("isRead")
    public boolean isRead() { return isRead; }
    public Instant getSentAt() { return sentAt; }
    public Long getTargetId() { return targetId; }
}
