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

    @SuppressWarnings("unchecked")
    private List<Notification> capturedSaveAll() {
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(notificationPort).saveAll(captor.capture());
        return captor.getValue();
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
        List<Notification> saved = capturedSaveAll();
        assertEquals(1, saved.size());
        assertEquals(42L, saved.get(0).getFanId());
        assertEquals(NotificationType.PAYMENT_SUCCESS, saved.get(0).getType());
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
        List<Notification> saved = capturedSaveAll();
        assertEquals(1, saved.size());
        assertEquals(NotificationType.PAYMENT_FAILED, saved.get(0).getType());
        assertEquals("결제가 실패하였습니다. 다시 시도해 주세요.", saved.get(0).getMessage());
        assertEquals(55L, saved.get(0).getFanId());
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
        verify(notificationPort, never()).saveAll(any());
        verify(outboxEventPort).update(event);
    }

    @Test
    @DisplayName("RESTOCK — payload JSON에서 fanId 파싱, notification 저장")
    void process_restock_extractsFanIdFromPayload() {
        OutboxEvent event = buildEvent("RESTOCK", 500L, "{\"fanId\":88,\"productId\":500}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        List<Notification> saved = capturedSaveAll();
        assertEquals(1, saved.size());
        assertEquals(88L, saved.get(0).getFanId());
        assertEquals(NotificationType.RESTOCK, saved.get(0).getType());
        assertEquals("관심 상품이 재입고되었습니다.", saved.get(0).getMessage());
        verify(fanIdResolverPort, never()).findFanIdByOrderId(any());
    }

    @Test
    @DisplayName("PENDING 없음 — notification·update 호출 없음")
    void process_noPendingEvents_doesNothing() {
        when(outboxEventPort.findPending(50)).thenReturn(List.of());

        scheduler.process();

        verify(notificationPort, never()).saveAll(any());
        verify(outboxEventPort, never()).update(any());
    }

    @Test
    @DisplayName("RESTOCK — payload에 fanId 필드 없음 → notification 저장 안 함, retry 증가")
    void process_restock_fanIdMissingInPayload_incrementsRetry() {
        OutboxEvent event = buildEvent("RESTOCK", 500L, "{\"productId\":500}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));

        scheduler.process();

        assertEquals(1, event.getRetryCount());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        verify(notificationPort, never()).saveAll(any());
        verify(outboxEventPort).update(event);
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

    @Test
    @DisplayName("NEW_FEED — artistId 팔로워 전체에게 Batch 알림 저장")
    void process_newFeed_sendsToAllFollowers() {
        OutboxEvent event = buildEvent("NEW_FEED", 10L, "{\"artistId\":1,\"feedId\":10}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFollowerFanIdsByArtistId(1L)).thenReturn(List.of(101L, 102L, 103L));

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        List<Notification> saved = capturedSaveAll();
        assertEquals(3, saved.size());
        assertTrue(saved.stream().allMatch(n -> n.getType() == NotificationType.NEW_FEED));
        assertEquals(List.of(101L, 102L, 103L), saved.stream().map(Notification::getFanId).toList());
    }

    @Test
    @DisplayName("NEW_FEED — 팔로워 없으면 알림 저장 없이 PUBLISHED")
    void process_newFeed_noFollowers_publishedWithoutNotification() {
        OutboxEvent event = buildEvent("NEW_FEED", 10L, "{\"artistId\":1,\"feedId\":10}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFollowerFanIdsByArtistId(1L)).thenReturn(List.of());

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        verify(notificationPort, never()).saveAll(any());
    }

    @Test
    @DisplayName("NEW_COMMENT — parentId 있음 → 부모 댓글 작성자에게 알림")
    void process_newComment_withParentId_sendsToParentAuthor() {
        OutboxEvent event = buildEvent("NEW_COMMENT", 20L, "{\"commentId\":5,\"parentId\":3}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFanIdByCommentId(3L)).thenReturn(Optional.of(99L));

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        List<Notification> saved = capturedSaveAll();
        assertEquals(1, saved.size());
        assertEquals(99L, saved.get(0).getFanId());
        assertEquals(NotificationType.NEW_COMMENT, saved.get(0).getType());
    }

    @Test
    @DisplayName("NEW_COMMENT — parentId null(최상위 댓글) → 알림 없이 PUBLISHED")
    void process_newComment_noParentId_publishedWithoutNotification() {
        OutboxEvent event = buildEvent("NEW_COMMENT", 20L, "{\"commentId\":5,\"parentId\":null}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        verify(notificationPort, never()).saveAll(any());
    }

    @Test
    @DisplayName("ARTIST_SCHEDULE — artistId 팔로워 전체에게 Batch 알림 저장")
    void process_artistSchedule_sendsToAllFollowers() {
        OutboxEvent event = buildEvent("ARTIST_SCHEDULE", 30L, "{\"artistId\":2,\"scheduleId\":30}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFollowerFanIdsByArtistId(2L)).thenReturn(List.of(201L, 202L));

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        List<Notification> saved = capturedSaveAll();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(n -> n.getType() == NotificationType.ARTIST_SCHEDULE));
    }

    @Test
    @DisplayName("ARTIST_SCHEDULE — 팔로워 없으면 알림 저장 없이 PUBLISHED")
    void process_artistSchedule_noFollowers_publishedWithoutNotification() {
        OutboxEvent event = buildEvent("ARTIST_SCHEDULE", 30L, "{\"artistId\":2,\"scheduleId\":30}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFollowerFanIdsByArtistId(2L)).thenReturn(List.of());

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        verify(notificationPort, never()).saveAll(any());
    }

    @Test
    @DisplayName("NEW_COMMENT — parentId 있지만 아티스트멤버 댓글(fan_id 없음) → 알림 없이 PUBLISHED")
    void process_newComment_parentIdExists_parentIsArtistMember_noNotification() {
        // 아티스트멤버가 쓴 댓글(fan_id=null)에 대댓글이 달린 경우 — findFanIdByCommentId가 empty 반환
        OutboxEvent event = buildEvent("NEW_COMMENT", 20L, "{\"commentId\":7,\"parentId\":4}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));
        when(fanIdResolverPort.findFanIdByCommentId(4L)).thenReturn(Optional.empty());

        scheduler.process();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        verify(notificationPort, never()).saveAll(any());
    }

    @Test
    @DisplayName("NEW_FEED — payload에 artistId 누락 → retry 증가")
    void process_newFeed_missingArtistIdInPayload_incrementsRetry() {
        OutboxEvent event = buildEvent("NEW_FEED", 10L, "{\"feedId\":10}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));

        scheduler.process();

        assertEquals(1, event.getRetryCount());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        verify(notificationPort, never()).saveAll(any());
    }

    @Test
    @DisplayName("ARTIST_SCHEDULE — payload에 artistId 누락 → retry 증가")
    void process_artistSchedule_missingArtistIdInPayload_incrementsRetry() {
        OutboxEvent event = buildEvent("ARTIST_SCHEDULE", 30L, "{\"scheduleId\":30}");
        when(outboxEventPort.findPending(50)).thenReturn(List.of(event));

        scheduler.process();

        assertEquals(1, event.getRetryCount());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        verify(notificationPort, never()).saveAll(any());
    }
}
