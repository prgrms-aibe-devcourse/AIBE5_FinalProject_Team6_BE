package com.fandrops.notification.infrastructure;

import com.fandrops.notification.application.port.FanIdResolverPort;
import com.fandrops.notification.domain.Notification;
import com.fandrops.notification.domain.NotificationType;
import com.fandrops.notification.domain.OutboxEvent;
import com.fandrops.notification.domain.port.NotificationPort;
import com.fandrops.notification.domain.port.OutboxEventPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventScheduler.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventPort outboxEventPort;
    private final NotificationPort notificationPort;
    private final FanIdResolverPort fanIdResolverPort;

    public OutboxEventScheduler(OutboxEventPort outboxEventPort,
                                NotificationPort notificationPort,
                                FanIdResolverPort fanIdResolverPort) {
        this.outboxEventPort = outboxEventPort;
        this.notificationPort = notificationPort;
        this.fanIdResolverPort = fanIdResolverPort;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void process() {
        List<OutboxEvent> pending = outboxEventPort.findPending(BATCH_SIZE);
        for (OutboxEvent event : pending) {
            try {
                processEvent(event);
                event.markPublished(Instant.now());
            } catch (Exception e) {
                log.warn("Outbox 처리 실패: eventId={}, eventType={}, retry={}",
                        event.getId(), event.getEventType(), event.getRetryCount(), e);
                event.incrementRetry();
            }
            outboxEventPort.update(event);
        }
    }

    private void processEvent(OutboxEvent event) {
        NotificationType type = NotificationType.valueOf(event.getEventType());
        Long fanId = resolveFanId(type, event);
        if (fanId == null) {
            log.warn("fanId 조회 실패: eventId={}, eventType={}", event.getId(), event.getEventType());
            throw new IllegalStateException("fanId를 찾을 수 없습니다: resourceId=" + event.getResourceId());
        }

        Notification notification = Notification.builder()
                .fanId(fanId)
                .type(type)
                .targetId(event.getResourceId())
                .message(resolveMessage(type))
                .sentAt(Instant.now())
                .build();

        notificationPort.save(notification);
    }

    private Long resolveFanId(NotificationType type, OutboxEvent event) {
        return switch (type) {
            case PAYMENT_SUCCESS -> fanIdResolverPort.findFanIdByOrderId(event.getResourceId()).orElse(null);
            case ARTIST_APPLICATION_APPROVED -> extractLong(event.getPayload(), "fanId");
            default -> null;
        };
    }

    private String resolveMessage(NotificationType type) {
        return switch (type) {
            case PAYMENT_SUCCESS -> "결제가 완료되었습니다.";
            case PAYMENT_FAILED -> "결제가 실패하였습니다. 다시 시도해 주세요.";
            case ARTIST_APPLICATION_APPROVED -> "아티스트 입점 신청이 승인되었습니다.";
            case RESTOCK -> "관심 상품이 재입고되었습니다.";
            case NEW_FEED -> "팔로우한 아티스트가 새 피드를 등록했습니다.";
            case NEW_COMMENT -> "내 글에 댓글이 달렸습니다.";
            case ARTIST_SCHEDULE -> "팔로우한 아티스트의 새로운 일정이 등록되었습니다.";
        };
    }

    private Long extractLong(String payload, String fieldName) {
        String key = "\"" + fieldName + "\":";
        int start = payload.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        int end = payload.length();
        for (int i = start; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (c == ',' || c == '}') { end = i; break; }
        }
        try {
            return Long.parseLong(payload.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
