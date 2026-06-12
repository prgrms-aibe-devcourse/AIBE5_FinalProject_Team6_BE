package com.fandrops.community.application.port;

import java.util.Map;

public record OutboxEvent(
        OutboxEventType type,
        Long aggregateId,
        Map<String, Object> payload
) {}