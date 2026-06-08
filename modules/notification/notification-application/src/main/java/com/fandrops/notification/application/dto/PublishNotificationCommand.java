package com.fandrops.notification.application.dto;

import java.util.Objects;

public class PublishNotificationCommand {

    private final String eventType;
    private final Long resourceId;
    private final String payload;

    public PublishNotificationCommand(String eventType, Long resourceId, String payload) {
        this.eventType = Objects.requireNonNull(eventType, "eventType은 필수입니다");
        this.resourceId = resourceId;
        this.payload = Objects.requireNonNull(payload, "payload는 필수입니다");
    }

    public String getEventType() { return eventType; }
    public Long getResourceId() { return resourceId; }
    public String getPayload() { return payload; }
}
