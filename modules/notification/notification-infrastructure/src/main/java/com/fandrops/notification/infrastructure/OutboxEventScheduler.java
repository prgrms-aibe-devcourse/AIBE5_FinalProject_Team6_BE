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
        List<Long> fanIds = resolveAllFanIds(type, event);
        if (fanIds.isEmpty()) {
            log.debug("알림 수신자 없음 (정상 skip): eventId={}, eventType={}", event.getId(), event.getEventType());
            return;
        }

        Instant now = Instant.now();
        String message = resolveMessage(type, event);
        List<Notification> notifications = fanIds.stream()
                .map(fanId -> Notification.builder()
                        .fanId(fanId)
                        .type(type)
                        .targetId(event.getResourceId())
                        .message(message)
                        .sentAt(now)
                        .build())
                .toList();

        notificationPort.saveAll(notifications);
    }

    private List<Long> resolveAllFanIds(NotificationType type, OutboxEvent event) {
        return switch (type) {
            case PAYMENT_SUCCESS, PAYMENT_FAILED ->
                    fanIdResolverPort.findFanIdByOrderId(event.getResourceId())
                            .map(List::of)
                            .orElseThrow(() -> new IllegalStateException(
                                    "fanId 조회 실패: resourceId=" + event.getResourceId()));
            case ARTIST_APPLICATION_APPROVED, RESTOCK -> {
                Long fanId = extractLong(event.getPayload(), "fanId");
                if (fanId == null) throw new IllegalStateException("fanId 누락: payload=" + event.getPayload());
                yield List.of(fanId);
            }
            case NEW_FEED, ARTIST_SCHEDULE -> {
                Long artistId = extractLong(event.getPayload(), "artistId");
                if (artistId == null) throw new IllegalStateException("artistId 누락: payload=" + event.getPayload());
                yield fanIdResolverPort.findFollowerFanIdsByArtistId(artistId);
            }
            case NEW_COMMENT -> {
                Long parentId = extractLong(event.getPayload(), "parentId");
                if (parentId == null) yield List.of();
                yield fanIdResolverPort.findFanIdByCommentId(parentId)
                        .map(List::of).orElse(List.of());
            }
            default -> List.of();
        };
    }

    private String resolveMessage(NotificationType type, OutboxEvent event) {
        return switch (type) {
            case PAYMENT_SUCCESS -> "결제가 완료되었습니다.";
            case PAYMENT_FAILED -> "결제가 실패하였습니다. 다시 시도해 주세요.";
            case ARTIST_APPLICATION_APPROVED -> "아티스트 입점 신청이 승인되었습니다.";
            case RESTOCK -> {
                String name = extractString(event.getPayload(), "productName");
                yield (name != null ? name : "관심 상품") + "이(가) 재입고되었어요!";
            }
            case NEW_FEED -> "팔로우한 아티스트가 새 피드를 등록했습니다.";
            case NEW_COMMENT -> "내 글에 댓글이 달렸습니다.";
            case ARTIST_SCHEDULE -> "팔로우한 아티스트의 새로운 일정이 등록되었습니다.";
        };
    }

    private String extractString(String payload, String fieldName) {
        String key = "\"" + fieldName + "\":\"";
        int start = payload.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        int end = payload.indexOf("\"", start);
        return end == -1 ? null : payload.substring(start, end);
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
