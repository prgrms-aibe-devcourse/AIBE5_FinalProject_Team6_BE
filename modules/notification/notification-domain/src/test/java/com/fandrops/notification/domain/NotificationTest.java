package com.fandrops.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");

    private Notification buildDefault() {
        return Notification.builder()
                .fanId(1L)
                .type(NotificationType.PAYMENT_SUCCESS)
                .message("결제가 완료되었습니다.")
                .sentAt(NOW)
                .build();
    }

    @Test
    @DisplayName("정상 생성 — isRead 기본값 false")
    void build_success_defaultIsReadFalse() {
        Notification n = buildDefault();
        assertEquals(1L, n.getFanId());
        assertEquals(NotificationType.PAYMENT_SUCCESS, n.getType());
        assertFalse(n.isRead());
        assertNull(n.getId());
        assertNull(n.getTargetId());
    }

    @Test
    @DisplayName("fanId null → NullPointerException")
    void build_nullFanId_throws() {
        assertThrows(NullPointerException.class, () ->
                Notification.builder()
                        .type(NotificationType.PAYMENT_SUCCESS)
                        .message("msg")
                        .sentAt(NOW)
                        .build()
        );
    }

    @Test
    @DisplayName("type null → NullPointerException")
    void build_nullType_throws() {
        assertThrows(NullPointerException.class, () ->
                Notification.builder()
                        .fanId(1L)
                        .message("msg")
                        .sentAt(NOW)
                        .build()
        );
    }

    @Test
    @DisplayName("sentAt null → NullPointerException")
    void build_nullSentAt_throws() {
        assertThrows(NullPointerException.class, () ->
                Notification.builder()
                        .fanId(1L)
                        .type(NotificationType.PAYMENT_SUCCESS)
                        .message("msg")
                        .build()
        );
    }

    @Test
    @DisplayName("markAsRead() 호출 후 isRead=true")
    void markAsRead_changesIsReadToTrue() {
        Notification n = buildDefault();
        assertFalse(n.isRead());
        n.markAsRead();
        assertTrue(n.isRead());
    }

    @Test
    @DisplayName("targetId 포함 생성 정상")
    void build_withTargetId_success() {
        Notification n = Notification.builder()
                .fanId(1L)
                .type(NotificationType.NEW_FEED)
                .targetId(42L)
                .message("새 피드가 등록되었습니다.")
                .sentAt(NOW)
                .build();
        assertEquals(42L, n.getTargetId());
    }
}
