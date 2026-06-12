package com.fandrops.user.domain;

import java.time.Instant;

public class AuditLog {

    private final Long id;
    private final Instant occurredAt;
    private final String actorType;
    private final Long actorId;
    private final String action;
    private final String resourceType;
    private final Long resourceId;
    private final String traceId;
    private final String beforeJson;
    private final String afterJson;
    private final String reason;
    private final String clientIp;

    private AuditLog(Builder builder) {
        this.id = builder.id;
        this.occurredAt = builder.occurredAt != null ? builder.occurredAt : Instant.now();
        this.actorType = builder.actorType;
        this.actorId = builder.actorId;
        this.action = builder.action;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.traceId = builder.traceId;
        this.beforeJson = builder.beforeJson;
        this.afterJson = builder.afterJson;
        this.reason = builder.reason;
        this.clientIp = builder.clientIp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActorType() { return actorType; }
    public Long getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public String getTraceId() { return traceId; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public String getReason() { return reason; }
    public String getClientIp() { return clientIp; }

    public static class Builder {
        private Long id;
        private Instant occurredAt;
        private String actorType;
        private Long actorId;
        private String action;
        private String resourceType;
        private Long resourceId;
        private String traceId;
        private String beforeJson;
        private String afterJson;
        private String reason;
        private String clientIp;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder occurredAt(Instant occurredAt) { this.occurredAt = occurredAt; return this; }
        public Builder actorType(String actorType) { this.actorType = actorType; return this; }
        public Builder actorId(Long actorId) { this.actorId = actorId; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder resourceType(String resourceType) { this.resourceType = resourceType; return this; }
        public Builder resourceId(Long resourceId) { this.resourceId = resourceId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder beforeJson(String beforeJson) { this.beforeJson = beforeJson; return this; }
        public Builder afterJson(String afterJson) { this.afterJson = afterJson; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder clientIp(String clientIp) { this.clientIp = clientIp; return this; }

        public AuditLog build() {
            return new AuditLog(this);
        }
    }
}
