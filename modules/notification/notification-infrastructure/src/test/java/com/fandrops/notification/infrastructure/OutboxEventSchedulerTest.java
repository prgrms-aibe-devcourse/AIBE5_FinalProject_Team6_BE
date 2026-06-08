package com.fandrops.notification.infrastructure;

import com.fandrops.notification.application.port.FanIdResolverPort;
import com.fandrops.notification.domain.Notification;
import com.fandrops.notification.domain.NotificationType;
import com.fandrops.notification.domain.OutboxEvent;
import com.fandrops.notification.domain.OutboxStatus;
import com.fandrops.notification.domain.port.NotificationPort;
import com.fandrops.notification.domain.port.OutboxEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventSchedulerTest {

    @Mock private OutboxEventPort outboxEventPort;
    @Mock private NotificationPort notificationPort;
    @Mock private FanIdResolverPort fanIdResolverPort;

    private OutboxEventScheduler scheduler;

    private static final Instant FIXED_NOW = Instant.parse("2026-06-08T00:00:00Z");

    @BeforeEach
    void setUp() {
        scheduler = new OutboxEventScheduler(outboxEventPort, notificationPort, fanIdResolverPort);
    }

    private OutboxEvent buildEvent(String eventType, Long resourceId, String payload) {
        return OutboxEvent.builder()
                .id(1L)
                .eventType(eventType)
                .resourceId(resourceId)
                .payload(payload)
                .createdAt(FIXED_NOW)
                .build();
    }

    @Test
    @DisplayName("PAYMENT_SUCCESS 처리 성공 — notification 저장, status=PUBLISHED")
    void process_paymentSuccess_savesNotificationAndPublishes() {
        OutboxEvent event = buildEvent("PAYMENT_SUCCESS", 100L, "{\"orderId\":100}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFanIdByOrderId(100L)).thenReturn(Optional.of(42L));

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPort).save(captor.capture());
        assertEquals(42L, captor.getValue().getFanId());
        assertEquals(NotificationType.PAYMENT_SUCCESS, captor.getValue().getType());
        verify(outboxEventPort).update(event);
    }

    @Test
    @DisplayName("PAYMENT_FAILED 처리 성공 — findFanIdByOrderId 사용, notification 저장")
    void process_paymentFailed_savesNotificationAndPublishes() {
        OutboxEvent event = buildEvent("PAYMENT_FAILED", 200L, "{\"orderId\":200}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFanIdByOrderId(200L)).thenReturn(Optional.of(55L));

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPort).save(captor.capture());
        assertEquals(NotificationType.PAYMENT_FAILED, captor.getValue().getType());
        assertEquals("결제가 실패하였습니다. 다시 시도해 주세요.", captor.getValue().getMessage());
        assertEquals(55L, captor.getValue().getFanId());
    }

    @Test
    @DisplayName("ARTIST_APPLICATION_APPROVED — payload JSON에서 fanId 파싱")
    void process_artistApplicationApproved_extractsFanIdFromPayload() {
        OutboxEvent event = buildEvent("ARTIST_APPLICATION_APPROVED", 1L, "{\"fanId\":77}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));

        scheduler.process();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPort).save(captor.capture());
        assertEquals(77L, captor.getValue().getFanId());
        verify(fanIdResolverPort, never()).findFanIdByOrderId(any());
    }

    @Test
    @DisplayName("fanId 조회 실패 — notification 저장 안함, retryCount 1 증가")
    void process_fanIdNotFound_incrementsRetryWithoutSavingNotification() {
        OutboxEvent event = buildEvent("PAYMENT_SUCCESS", 999L, "{}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFanIdByOrderId(999L)).thenReturn(Optional.empty());

        scheduler.process();

        assertEquals(1, event.getRetryCount());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        verify(notificationPort, never()).save(any());
        verify(outboxEventPort).update(event);
    }

    @Test
    @DisplayName("PENDING 없음 — notification·update 호출 없음")
    void process_noPendingEvents_doesNothing() {
        when(outboxEventPort.findPending(50)).thenReturn(List.of());

        scheduler.process();

        verify(notificationPort, never()).save(any());
        verify(outboxEventPort, never()).update(any());
    }

    @Test
    @DisplayName("처리 실패 5회 — status=FAILED (DLQ 전환)")
    void process_5consecutiveFailures_eventBecomeFailed() {
        OutboxEvent event = buildEvent("PAYMENT_SUCCESS", 100L, "{}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFanIdByOrderId(100L)).thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            scheduler.process();
        }

        assertEquals(5, event.getRetryCount());
        assertEquals(OutboxStatus.FAILED, event.getStatus());
    }
}
