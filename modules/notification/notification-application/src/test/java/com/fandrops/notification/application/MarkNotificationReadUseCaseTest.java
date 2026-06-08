package com.fandrops.notification.application;

import com.fandrops.notification.application.exception.NotificationNotFoundException;
import com.fandrops.notification.domain.Notification;
import com.fandrops.notification.domain.NotificationType;
import com.fandrops.notification.domain.port.NotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarkNotificationReadUseCaseTest {

    @Mock
    private NotificationPort notificationPort;

    private MarkNotificationReadUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new MarkNotificationReadUseCase(notificationPort);
    }

    private Notification buildNotification(Long id, Long fanId) {
        return Notification.builder()
                .id(id)
                .fanId(fanId)
                .type(NotificationType.PAYMENT_SUCCESS)
                .message("결제가 완료되었습니다.")
                .sentAt(Instant.parse("2026-06-08T00:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("정상 읽음 처리 — isRead=true 상태로 저장")
    void markAsRead_success_savesWithReadTrue() {
        Notification notification = buildNotification(1L, 42L);
        when(notificationPort.findByIdAndFanId(1L, 42L)).thenReturn(Optional.of(notification));
        when(notificationPort.save(any())).thenReturn(notification);

        useCase.markAsRead(1L, 42L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPort).save(captor.capture());
        assertTrue(captor.getValue().isRead());
    }

    @Test
    @DisplayName("존재하지 않는 알림 — NotificationNotFoundException")
    void markAsRead_notFound_throwsException() {
        when(notificationPort.findByIdAndFanId(999L, 42L)).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class,
                () -> useCase.markAsRead(999L, 42L));

        verify(notificationPort, never()).save(any());
    }

    @Test
    @DisplayName("다른 fanId의 알림 — NotificationNotFoundException (타인 알림 접근 불가)")
    void markAsRead_wrongFanId_throwsException() {
        when(notificationPort.findByIdAndFanId(1L, 99L)).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class,
                () -> useCase.markAsRead(1L, 99L));
    }
}
