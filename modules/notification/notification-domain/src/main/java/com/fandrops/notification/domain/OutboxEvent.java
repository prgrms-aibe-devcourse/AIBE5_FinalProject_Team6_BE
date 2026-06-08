package com.fandrops.notification.domain;

import java.time.Instant;
import java.util.Objects;

public class OutboxEvent {

    private static final int MAX_RETRY_COUNT = 5;

    private final Long id;
    private final String eventType;
    private final Long resourceId;
    private final String payload;
    private OutboxStatus status;
    private int retryCount;
    private final Instant createdAt;
    private Instant publishedAt;

    private OutboxEvent(Builder builder) {
        this.id = builder.id;
        this.eventType = builder.eventType;
        this.resourceId = builder.resourceId;
        this.payload = builder.payload;
        this.status = builder.status;
        this.retryCount = builder.retryCount;
        this.createdAt = builder.createdAt;
        this.publishedAt = builder.publishedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    public void incrementRetry() {
        this.retryCount++;
        if (this.retryCount >= MAX_RETRY_COUNT) {
            this.status = OutboxStatus.FAILED;
        }
    }

    public boolean isPending() {
        return OutboxStatus.PENDING == this.status;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public Long getResourceId() { return resourceId; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public static class Builder {
        private Long id;
        private String eventType;
        private Long resourceId;
        private String payload;
        private OutboxStatus status = OutboxStatus.PENDING;
        private int retryCount = 0;
        private Instant createdAt;
        private Instant publishedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder resourceId(Long resourceId) { this.resourceId = resourceId; return this; }
        public Builder payload(String payload) { this.payload = payload; return this; }
        public Builder status(OutboxStatus status) { this.status = status; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder publishedAt(Instant publishedAt) { this.publishedAt = publishedAt; return this; }

        public OutboxEvent build() {
            Objects.requireNonNull(eventType, "eventType은 필수입니다");
            Objects.requireNonNull(payload, "payload는 필수입니다");
            Objects.requireNonNull(createdAt, "createdAt은 필수입니다");
            return new OutboxEvent(this);
        }
    }
}
