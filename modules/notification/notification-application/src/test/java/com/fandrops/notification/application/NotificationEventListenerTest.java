package com.fandrops.notification.application;

import com.fandrops.community.application.event.ArtistScheduleEvent;
import com.fandrops.community.application.event.NewCommentEvent;
import com.fandrops.community.application.event.NewFeedEvent;
import com.fandrops.notification.application.dto.PublishNotificationCommand;
import com.fandrops.order.application.event.RestockAlertEvent;
import com.fandrops.payment.application.payment.PaymentApprovedEvent;
import com.fandrops.payment.application.payment.PaymentFailedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock PublishNotificationUseCase publishNotificationUseCase;
    @Mock MeterRegistry meterRegistry;

    NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        Counter counter = mock(Counter.class);
        // 실패 케이스에서만 호출됨 — 성공 케이스의 불필요한 stub 검출을 피하기 위해 lenient 사용
        lenient().when(meterRegistry.counter(anyString(), anyString(), anyString())).thenReturn(counter);
        listener = new NotificationEventListener(publishNotificationUseCase, meterRegistry);
    }

    // ── handlePaymentApproved ─────────────────────────────────────────────────

    @Test
    @DisplayName("결제 승인 이벤트 → PAYMENT_SUCCESS 커맨드로 publish 호출")
    void handlePaymentApproved_success_publishesCorrectCommand() {
        ArgumentCaptor<PublishNotificationCommand> captor =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);

        listener.handlePaymentApproved(new PaymentApprovedEvent(1L));

        verify(publishNotificationUseCase).publish(captor.capture());
        assertEquals("PAYMENT_SUCCESS", captor.getValue().getEventType());
        assertEquals(1L, captor.getValue().getResourceId());
    }

    @Test
    @DisplayName("결제 승인 이벤트 publish 실패 → 예외 전파 없음 + counter increment")
    void handlePaymentApproved_publishFails_doesNotThrowAndIncrementsCounter() {
        Counter counter = mock(Counter.class);
        when(meterRegistry.counter("fandrops.notification.failures", "eventType", "PAYMENT_SUCCESS"))
                .thenReturn(counter);
        doThrow(new RuntimeException("DB error"))
                .when(publishNotificationUseCase).publish(any());

        assertDoesNotThrow(() -> listener.handlePaymentApproved(new PaymentApprovedEvent(1L)));

        verify(counter).increment();
    }

    // ── handlePaymentFailed ───────────────────────────────────────────────────

    @Test
    @DisplayName("결제 실패 이벤트 → PAYMENT_FAILED 커맨드로 publish 호출")
    void handlePaymentFailed_success_publishesCorrectCommand() {
        ArgumentCaptor<PublishNotificationCommand> captor =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);

        listener.handlePaymentFailed(new PaymentFailedEvent(2L));

        verify(publishNotificationUseCase).publish(captor.capture());
        assertEquals("PAYMENT_FAILED", captor.getValue().getEventType());
        assertEquals(2L, captor.getValue().getResourceId());
    }

    @Test
    @DisplayName("결제 실패 이벤트 publish 실패 → 예외 전파 없음")
    void handlePaymentFailed_publishFails_doesNotThrow() {
        doThrow(new RuntimeException("DB error"))
                .when(publishNotificationUseCase).publish(any());

        assertDoesNotThrow(() -> listener.handlePaymentFailed(new PaymentFailedEvent(2L)));
    }

    // ── handleRestockAlert ────────────────────────────────────────────────────

    @Test
    @DisplayName("재입고 이벤트 → RESTOCK 커맨드로 publish 호출")
    void handleRestockAlert_success_publishesCorrectCommand() {
        ArgumentCaptor<PublishNotificationCommand> captor =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);

        listener.handleRestockAlert(new RestockAlertEvent(10L, 99L, "테스트 상품"));

        verify(publishNotificationUseCase).publish(captor.capture());
        assertEquals("RESTOCK", captor.getValue().getEventType());
        assertEquals(99L, captor.getValue().getResourceId());
    }

    @Test
    @DisplayName("재입고 이벤트 publish 실패 → 예외 전파 없음")
    void handleRestockAlert_publishFails_doesNotThrow() {
        doThrow(new RuntimeException("DB error"))
                .when(publishNotificationUseCase).publish(any());

        assertDoesNotThrow(() -> listener.handleRestockAlert(new RestockAlertEvent(10L, 99L, "테스트 상품")));
    }

    // ── handleNewFeed ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("새 피드 이벤트 → NEW_FEED 커맨드로 publish 호출")
    void handleNewFeed_success_publishesCorrectCommand() {
        ArgumentCaptor<PublishNotificationCommand> captor =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);

        listener.handleNewFeed(new NewFeedEvent(5L, 3L));

        verify(publishNotificationUseCase).publish(captor.capture());
        assertEquals("NEW_FEED", captor.getValue().getEventType());
        assertEquals(5L, captor.getValue().getResourceId());
    }

    @Test
    @DisplayName("새 피드 이벤트 publish 실패 → 예외 전파 없음")
    void handleNewFeed_publishFails_doesNotThrow() {
        doThrow(new RuntimeException("DB error"))
                .when(publishNotificationUseCase).publish(any());

        assertDoesNotThrow(() -> listener.handleNewFeed(new NewFeedEvent(5L, 3L)));
    }

    // ── handleNewComment ──────────────────────────────────────────────────────

    @Test
    @DisplayName("댓글 이벤트 → NEW_COMMENT 커맨드로 publish 호출")
    void handleNewComment_success_publishesCorrectCommand() {
        ArgumentCaptor<PublishNotificationCommand> captor =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);

        listener.handleNewComment(new NewCommentEvent(7L, 5L, 6L, 3L));

        verify(publishNotificationUseCase).publish(captor.capture());
        assertEquals("NEW_COMMENT", captor.getValue().getEventType());
        // resourceId는 feedId(5L)
        assertEquals(5L, captor.getValue().getResourceId());
    }

    @Test
    @DisplayName("댓글 이벤트 publish 실패 → 예외 전파 없음")
    void handleNewComment_publishFails_doesNotThrow() {
        doThrow(new RuntimeException("DB error"))
                .when(publishNotificationUseCase).publish(any());

        assertDoesNotThrow(() -> listener.handleNewComment(new NewCommentEvent(7L, 5L, 6L, 3L)));
    }

    // ── handleArtistSchedule ──────────────────────────────────────────────────

    @Test
    @DisplayName("아티스트 일정 이벤트 → ARTIST_SCHEDULE 커맨드로 publish 호출")
    void handleArtistSchedule_success_publishesCorrectCommand() {
        ArgumentCaptor<PublishNotificationCommand> captor =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);

        listener.handleArtistSchedule(new ArtistScheduleEvent(8L, 3L));

        verify(publishNotificationUseCase).publish(captor.capture());
        assertEquals("ARTIST_SCHEDULE", captor.getValue().getEventType());
        assertEquals(8L, captor.getValue().getResourceId());
    }

    @Test
    @DisplayName("아티스트 일정 이벤트 publish 실패 → 예외 전파 없음")
    void handleArtistSchedule_publishFails_doesNotThrow() {
        doThrow(new RuntimeException("DB error"))
                .when(publishNotificationUseCase).publish(any());

        assertDoesNotThrow(() -> listener.handleArtistSchedule(new ArtistScheduleEvent(8L, 3L)));
    }
}
