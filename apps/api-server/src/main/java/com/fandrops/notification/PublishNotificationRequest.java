package com.fandrops.notification;

public record PublishNotificationRequest(
        String eventType,
        Long resourceId,
        String payload
) {}