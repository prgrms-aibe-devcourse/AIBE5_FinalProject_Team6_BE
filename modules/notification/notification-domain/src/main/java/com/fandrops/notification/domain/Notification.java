package com.fandrops.notification.domain;

import java.time.Instant;
import java.util.Objects;

public class Notification {

    private final Long id;
    private final Long fanId;
    private final NotificationType type;
    private final Long targetId;
    private final String message;
    private boolean read;
    private final Instant sentAt;

    private Notification(Builder builder) {
        this.id = builder.id;
        this.fanId = builder.fanId;
        this.type = builder.type;
        this.targetId = builder.targetId;
        this.message = builder.message;
        this.read = builder.read;
        this.sentAt = builder.sentAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void markAsRead() {
        this.read = true;
    }

    public Long getId() { return id; }
    public Long getFanId() { return fanId; }
    public NotificationType getType() { return type; }
    public Long getTargetId() { return targetId; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public Instant getSentAt() { return sentAt; }

    public static class Builder {
        private Long id;
        private Long fanId;
        private NotificationType type;
        private Long targetId;
        private String message;
        private boolean read = false;
        private Instant sentAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder fanId(Long fanId) { this.fanId = fanId; return this; }
        public Builder type(NotificationType type) { this.type = type; return this; }
        public Builder targetId(Long targetId) { this.targetId = targetId; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder read(boolean read) { this.read = read; return this; }
        public Builder sentAt(Instant sentAt) { this.sentAt = sentAt; return this; }

        public Notification build() {
            Objects.requireNonNull(fanId, "fanId는 필수입니다");
            Objects.requireNonNull(type, "type은 필수입니다");
            Objects.requireNonNull(message, "message는 필수입니다");
            Objects.requireNonNull(sentAt, "sentAt은 필수입니다");
            return new Notification(this);
        }
    }
}
