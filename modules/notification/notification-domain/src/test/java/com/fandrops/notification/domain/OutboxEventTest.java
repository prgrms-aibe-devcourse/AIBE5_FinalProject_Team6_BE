package com.fandrops.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");

    private OutboxEvent buildDefault() {
        return OutboxEvent.builder()
                .eventType("PAYMENT_SUCCESS")
                .resourceId(100L)
                .payload("{\"orderId\":100}")
                .createdAt(NOW)
                .build();
    }

    @Test
    @DisplayName("정상 생성 — status=PENDING, retryCount=0")
    void build_success_defaultsPendingAndZeroRetry() {
        OutboxEvent event = buildDefault();
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getRetryCount());
        assertNull(event.getPublishedAt());
        assertTrue(event.isPending());
    }

    @Test
    @DisplayName("eventType null → NullPointerException")
    void build_nullEventType_throws() {
        assertThrows(NullPointerException.class, () ->
                OutboxEvent.builder()
                        .payload("{}")
                        .createdAt(NOW)
                        .build()
        );
    }

    @Test
    @DisplayName("markPublished() — status=PUBLISHED, publishedAt 설정")
    void markPublished_changesStatusAndSetsPublishedAt() {
        OutboxEvent event = buildDefault();
        Instant publishedAt = NOW.plusSeconds(5);
        event.markPublished(publishedAt);
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(publishedAt, event.getPublishedAt());
        assertFalse(event.isPending());
    }

    @Test
    @DisplayName("incrementRetry() 4회 — status 여전히 PENDING")
    void incrementRetry_4times_stillPending() {
        OutboxEvent event = buildDefault();
        for (int i = 0; i < 4; i++) event.incrementRetry();
        assertEquals(4, event.getRetryCount());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
    }

    @Test
    @DisplayName("incrementRetry() 5회 — status=FAILED (DLQ 기준)")
    void incrementRetry_5times_becomesFailed() {
        OutboxEvent event = buildDefault();
        for (int i = 0; i < 5; i++) event.incrementRetry();
        assertEquals(5, event.getRetryCount());
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertFalse(event.isPending());
    }
}
