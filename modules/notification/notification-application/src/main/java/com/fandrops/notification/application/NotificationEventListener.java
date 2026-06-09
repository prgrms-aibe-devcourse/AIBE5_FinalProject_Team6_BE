package com.fandrops.notification.application;

import com.fandrops.notification.application.dto.PublishNotificationCommand;
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

    // TODO [형성빈]: inventory-application에 RestockAlertEvent(Long fanId, Long productId, String productName) 추가 후 아래 활성화
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // public void handleRestockAlert(RestockAlertEvent event) {
    //     PublishNotificationCommand command = new PublishNotificationCommand(
    //             "RESTOCK", event.getProductId(), "{\"fanId\":" + event.getFanId() + ",\"productId\":" + event.getProductId() + "}");
    //     publishNotificationUseCase.publish(command);
    // }

    // TODO [정환철]: community-application에 NewFeedEvent(Long fanId, Long feedId, Long artistId) 추가 후 아래 활성화
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // public void handleNewFeed(NewFeedEvent event) {
    //     PublishNotificationCommand command = new PublishNotificationCommand(
    //             "NEW_FEED", event.getFeedId(), "{\"fanId\":" + event.getFanId() + ",\"feedId\":" + event.getFeedId() + "}");
    //     publishNotificationUseCase.publish(command);
    // }

    // TODO [정환철]: community-application에 NewCommentEvent(Long fanId, Long feedId, Long commentId) 추가 후 아래 활성화
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // public void handleNewComment(NewCommentEvent event) { ... }

    // TODO [정환철]: community-application에 ArtistScheduleEvent(Long fanId, Long scheduleId, Long artistId) 추가 후 아래 활성화
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // public void handleArtistSchedule(ArtistScheduleEvent event) { ... }
}
