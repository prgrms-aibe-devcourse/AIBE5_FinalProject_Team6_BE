package com.fandrops.payment.api.queue;

import com.fandrops.payment.application.queue.QueueAdvanceResult;
import com.fandrops.payment.application.queue.QueueStatusResult;
import com.fandrops.payment.application.queue.WaitQueueService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class QueueSsePushService {

    private static final Logger log = LoggerFactory.getLogger(QueueSsePushService.class);

    private final WaitQueueService waitQueueService;
    private final SseEmitterRegistry registry;

    @Value("${fandrops.queue.advance-batch-size:5}")
    private int advanceBatchSize;

    @Value("${fandrops.queue.max-concurrent-processing:10}")
    private long maxConcurrentProcessing;

    @Value("${fandrops.queue.processing-timeout-seconds:600}")
    private long processingTimeoutSeconds;

    @Value("${fandrops.queue.waiting-timeout-seconds:3600}")
    private long waitingTimeoutSeconds;

    public QueueSsePushService(WaitQueueService waitQueueService, SseEmitterRegistry registry) {
        this.waitQueueService = waitQueueService;
        this.registry = registry;
    }

    @Async("queueSseExecutor")
    public void processProduct(Long productId) {
        // 0. 장기 WAITING 타임아웃 → EXPIRED 전이 (stale entry 제거)
        Instant waitingThreshold = Instant.now().minusSeconds(waitingTimeoutSeconds);
        List<Long> waitingExpired = waitQueueService.expireWaitingTimeouts(productId, waitingThreshold);
        for (Long fanId : waitingExpired) {
            registry.sendToFan(productId, fanId, QueueStreamEvent.expired());
        }

        // 1. PROCESSING 타임아웃 → EXPIRED 전이 (invariants §4, W-3)
        Instant threshold = Instant.now().minusSeconds(processingTimeoutSeconds);
        List<Long> expired = waitQueueService.expireTimeouts(productId, threshold);
        for (Long fanId : expired) {
            registry.sendToFan(productId, fanId, QueueStreamEvent.expired());
        }

        // 2. WAITING → PROCESSING 진입 (W-3: maxConcurrent 이하로만 진입)
        List<QueueAdvanceResult> advanced =
                waitQueueService.advanceQueue(productId, advanceBatchSize, maxConcurrentProcessing);
        for (QueueAdvanceResult result : advanced) {
            registry.sendToFan(productId, result.getFanId(),
                    QueueStreamEvent.processing(result.getAccessToken()));
        }

        // 3. 아직 WAITING인 팬에게 현재 순번 푸시
        Map<Long, ?> connected = registry.getEmittersForProduct(productId);
        for (Long fanId : connected.keySet()) {
            try {
                QueueStatusResult status = waitQueueService.getStatus(fanId, productId);
                if ("WAITING".equals(status.getStatus())) {
                    registry.sendToFan(productId, fanId,
                            QueueStreamEvent.waiting(status.getPosition(), status.getEstimatedWaitSec()));
                }
            } catch (IllegalStateException e) {
                // 이탈하거나 entry가 없는 정상 케이스 — 무시
            } catch (Exception e) {
                log.warn("[QueueSsePush] 순번 푸시 실패 productId={} fanId={}", productId, fanId, e);
            }
        }
    }
}