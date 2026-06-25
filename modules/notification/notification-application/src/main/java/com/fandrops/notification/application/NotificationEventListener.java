package com.fandrops.notification.application;

import com.fandrops.community.application.event.ArtistScheduleEvent;
import com.fandrops.community.application.event.NewCommentEvent;
import com.fandrops.community.application.event.NewFeedEvent;
import com.fandrops.notification.application.dto.PublishNotificationCommand;
import com.fandrops.order.application.event.RestockAlertEvent;
import com.fandrops.payment.application.payment.PaymentApprovedEvent;
import com.fandrops.payment.application.payment.PaymentFailedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final PublishNotificationUseCase publishNotificationUseCase;
    private final MeterRegistry meterRegistry;

    public NotificationEventListener(PublishNotificationUseCase publishNotificationUseCase,
                                     MeterRegistry meterRegistry) {
        this.publishNotificationUseCase = publishNotificationUseCase;
        this.meterRegistry = meterRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentApproved(PaymentApprovedEvent event) {
        log.info("결제 승인 알림 이벤트 수신: orderId={}", event.getOrderId());
        publish("PAYMENT_SUCCESS", event.getOrderId(),
                "{\"orderId\":" + event.getOrderId() + "}");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("결제 실패 알림 이벤트 수신: orderId={}", event.getOrderId());
        publish("PAYMENT_FAILED", event.getOrderId(),
                "{\"orderId\":" + event.getOrderId() + "}");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRestockAlert(RestockAlertEvent event) {
        log.info("재입고 알림 이벤트 수신: fanId={}, productId={}", event.getFanId(), event.getProductId());
        String escapedName = event.getProductName().replace("\\", "\\\\").replace("\"", "\\\"");
        publish("RESTOCK", event.getProductId(),
                "{\"fanId\":" + event.getFanId() + ",\"productId\":" + event.getProductId() + ",\"productName\":\"" + escapedName + "\"}");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNewFeed(NewFeedEvent event) {
        log.info("새 피드 알림 이벤트 수신: feedId={}, artistId={}", event.getFeedId(), event.getArtistId());
        publish("NEW_FEED", event.getFeedId(),
                "{\"artistId\":" + event.getArtistId() + ",\"feedId\":" + event.getFeedId() + "}");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNewComment(NewCommentEvent event) {
        log.info("댓글 알림 이벤트 수신: commentId={}, parentId={}", event.getCommentId(), event.getParentId());
        publish("NEW_COMMENT", event.getFeedId(),
                "{\"commentId\":" + event.getCommentId() + ",\"parentId\":" + event.getParentId() + "}");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleArtistSchedule(ArtistScheduleEvent event) {
        log.info("아티스트 일정 알림 이벤트 수신: scheduleId={}, artistId={}", event.getScheduleId(), event.getArtistId());
        publish("ARTIST_SCHEDULE", event.getScheduleId(),
                "{\"artistId\":" + event.getArtistId() + ",\"scheduleId\":" + event.getScheduleId() + "}");
    }

    private void publish(String eventType, Long resourceId, String payload) {
        try {
            publishNotificationUseCase.publish(new PublishNotificationCommand(eventType, resourceId, payload));
        } catch (Exception e) {
            log.error("[NOTIFICATION_FAIL] Outbox 저장 실패 eventType={} resourceId={}", eventType, resourceId, e);
            meterRegistry.counter("fandrops.notification.failures",
                    "eventType", eventType).increment();
        }
    }
}
