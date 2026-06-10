package com.fandrops.notification.application;

import com.fandrops.notification.application.dto.PublishNotificationCommand;
import com.fandrops.order.application.event.RestockAlertEvent;
import com.fandrops.payment.application.payment.PaymentApprovedEvent;
import com.fandrops.payment.application.payment.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final PublishNotificationUseCase publishNotificationUseCase;

    public NotificationEventListener(PublishNotificationUseCase publishNotificationUseCase) {
        this.publishNotificationUseCase = publishNotificationUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentApproved(PaymentApprovedEvent event) {
        log.info("결제 승인 알림 이벤트 수신: orderId={}", event.getOrderId());
        PublishNotificationCommand command = new PublishNotificationCommand(
                "PAYMENT_SUCCESS",
                event.getOrderId(),
                "{\"orderId\":" + event.getOrderId() + "}"
        );
        publishNotificationUseCase.publish(command);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("결제 실패 알림 이벤트 수신: orderId={}", event.getOrderId());
        PublishNotificationCommand command = new PublishNotificationCommand(
                "PAYMENT_FAILED",
                event.getOrderId(),
                "{\"orderId\":" + event.getOrderId() + "}"
        );
        publishNotificationUseCase.publish(command);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRestockAlert(RestockAlertEvent event) {
        log.info("재입고 알림 이벤트 수신: fanId={}, productId={}", event.getFanId(), event.getProductId());
        PublishNotificationCommand command = new PublishNotificationCommand(
                "RESTOCK",
                event.getProductId(),
                "{\"fanId\":" + event.getFanId() + ",\"productId\":" + event.getProductId() + "}");
        publishNotificationUseCase.publish(command);
    }

    // TODO [표지민]: community-application dependency 추가 후 활성화
    // NewFeedEvent(Long feedId, Long artistId) — artistId로 팔로워 전체 조회 후 발송
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // public void handleNewFeed(NewFeedEvent event) {
    //     PublishNotificationCommand command = new PublishNotificationCommand(
    //             "NEW_FEED", event.getFeedId(), "{\"artistId\":" + event.getArtistId() + ",\"feedId\":" + event.getFeedId() + "}");
    //     publishNotificationUseCase.publish(command);
    // }

    // TODO [표지민]: community-application dependency 추가 후 활성화
    // NewCommentEvent(Long commentId, Long feedId, Long parentId, Long artistId) — artistId로 팔로워 전체 조회 후 발송
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // public void handleNewComment(NewCommentEvent event) {
    //     PublishNotificationCommand command = new PublishNotificationCommand(
    //             "NEW_COMMENT", event.getFeedId(), "{\"artistId\":" + event.getArtistId() + ",\"commentId\":" + event.getCommentId() + "}");
    //     publishNotificationUseCase.publish(command);
    // }

    // TODO [표지민]: community-application dependency 추가 후 활성화
    // ArtistScheduleEvent(Long scheduleId, Long artistId) — artistId로 팔로워 전체 조회 후 발송
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // public void handleArtistSchedule(ArtistScheduleEvent event) {
    //     PublishNotificationCommand command = new PublishNotificationCommand(
    //             "ARTIST_SCHEDULE", event.getScheduleId(), "{\"artistId\":" + event.getArtistId() + ",\"scheduleId\":" + event.getScheduleId() + "}");
    //     publishNotificationUseCase.publish(command);
    // }
}
