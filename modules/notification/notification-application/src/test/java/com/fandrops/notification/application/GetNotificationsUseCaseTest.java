package com.fandrops.notification.application;

import com.fandrops.notification.application.dto.NotificationResult;
import com.fandrops.notification.domain.Notification;
import com.fandrops.notification.domain.NotificationType;
import com.fandrops.notification.domain.port.NotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetNotificationsUseCaseTest {

    @Mock
    private NotificationPort notificationPort;

    private GetNotificationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetNotificationsUseCase(notificationPort);
    }

    private Notification buildNotification(Long id) {
        return Notification.builder()
                .id(id)
                .fanId(1L)
                .type(NotificationType.PAYMENT_SUCCESS)
                .message("결제가 완료되었습니다.")
                .sentAt(Instant.parse("2026-06-08T00:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("cursorId null — 첫 페이지 조회")
    void getNotifications_noCursor_callsWithNullCursor() {
        when(notificationPort.findByFanId(1L, null, 20))
                .thenReturn(List.of(buildNotification(1L)));

        List<NotificationResult> results = useCase.getNotifications(1L, null, null);

        assertEquals(1, results.size());
        verify(notificationPort).findByFanId(1L, null, 20);
    }

    @Test
    @DisplayName("cursorId 있음 — 해당 ID 이후 페이지 조회")
    void getNotifications_withCursor_passedToPort() {
        when(notificationPort.findByFanId(1L, 10L, 20))
                .thenReturn(List.of(buildNotification(5L)));

        useCase.getNotifications(1L, 10L, null);

        verify(notificationPort).findByFanId(1L, 10L, 20);
    }

    @Test
    @DisplayName("size 명시 — 해당 size로 조회")
    void getNotifications_withSize_usesGivenSize() {
        when(notificationPort.findByFanId(1L, null, 5)).thenReturn(List.of());

        useCase.getNotifications(1L, null, 5);

        verify(notificationPort).findByFanId(1L, null, 5);
    }

    @Test
    @DisplayName("도메인 객체 → NotificationResult 변환 검증")
    void getNotifications_mapsToResult() {
        Notification n = buildNotification(99L);
        when(notificationPort.findByFanId(1L, null, 20)).thenReturn(List.of(n));

        List<NotificationResult> results = useCase.getNotifications(1L, null, null);

        assertEquals(99L, results.get(0).getId());
        assertEquals("PAYMENT_SUCCESS", results.get(0).getType());
        assertFalse(results.get(0).isRead());
    }
}
