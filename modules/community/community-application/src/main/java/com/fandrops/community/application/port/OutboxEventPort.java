package com.fandrops.community.application.port;

public interface OutboxEventPort {
    void publish(OutboxEvent event);
    boolean existsEvent(Long aggregateId, OutboxEventType type);
}