package com.fandrops.payment.api.queue;

import com.fandrops.payment.application.queue.WaitQueueService;
import java.util.HashSet;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueAdvanceScheduler {

    private final WaitQueueService waitQueueService;
    private final SseEmitterRegistry registry;
    private final QueueSsePushService queueSsePushService;

    public QueueAdvanceScheduler(WaitQueueService waitQueueService,
                                 SseEmitterRegistry registry,
                                 QueueSsePushService queueSsePushService) {
        this.waitQueueService = waitQueueService;
        this.registry = registry;
        this.queueSsePushService = queueSsePushService;
    }

    @Scheduled(fixedDelayString = "${fandrops.queue.scheduler.heartbeat-ms:5000}")
    public void heartbeat() {
        registry.sendHeartbeat();
    }

    @Scheduled(fixedDelayString = "${fandrops.queue.scheduler.interval-ms:3000}")
    public void tick() {
        // SSE 단절 시에도 WAITING 팬을 처리하기 위해 SSE 연결 목록과 Redis WAITING 목록의 합집합을 순회
        Set<Long> productIds = new HashSet<>(registry.getActiveProductIds());
        productIds.addAll(waitQueueService.getActiveProductIds());
        for (Long productId : productIds) {
            queueSsePushService.processProduct(productId);
        }
    }
}