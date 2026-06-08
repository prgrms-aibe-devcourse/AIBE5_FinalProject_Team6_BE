package com.fandrops.community.application.port;

public enum OutboxEventType {
    NEW_FEED("NEW_FEED", "ARTIST_FEED"),
    NEW_COMMENT("NEW_COMMENT", "COMMENT"),
    LIVE_START("LIVE_START", "ARTIST_SCHEDULE");

    public final String eventType;
    public final String aggregateType;

    OutboxEventType(String eventType, String aggregateType) {
        this.eventType = eventType;
        this.aggregateType = aggregateType;
    }
}