package com.fandrops.payment.api.queue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRegistry {

    // key: "productId:fanId" → SseEmitter
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // key: "productId:fanId" → 등록 시각(ms) — heartbeat에서 proactive close 기준
    private final ConcurrentHashMap<String, Long> registrationTimes = new ConcurrentHashMap<>();

    @Value("${fandrops.queue.sse-timeout-ms:60000}")
    private long sseTimeoutMs;

    @Value("${fandrops.ratelimit.sse-max-emitters:2000}")
    private int sseMaxEmitters;

    private SseEmitter register(Long productId, Long fanId) {
        String key = key(productId, fanId);
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        registrationTimes.put(key, System.currentTimeMillis());
        emitters.put(key, emitter);
        // [P1] 재연결 시 이전 Emitter의 콜백이 새 Emitter를 삭제하지 않도록 값 비교 제거
        emitter.onCompletion(() -> {
            emitters.remove(key, emitter);
            registrationTimes.remove(key);
        });
        // onTimeout 안에서 complete()는 Spring이 이미 async context를 닫는 중이라 무효.
        // proactive close는 sendHeartbeat()에서 55초 기준으로 처리한다.
        emitter.onTimeout(() -> {
            emitters.remove(key, emitter);
            registrationTimes.remove(key);
        });
        emitter.onError(e -> {
            emitters.remove(key, emitter);
            registrationTimes.remove(key);
        });
        return emitter;
    }

    public void sendToFan(Long productId, Long fanId, QueueStreamEvent event) {
        SseEmitter emitter = emitters.get(key(productId, fanId));
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("queue").data(event));
        } catch (IOException e) {
            emitters.remove(key(productId, fanId));
        }
    }

    /** 현재 SSE 연결이 있는 productId 목록. */
    public Set<Long> getActiveProductIds() {
        return emitters.keySet().stream()
                .map(k -> Long.parseLong(k.split(":")[0]))
                .collect(Collectors.toSet());
    }

    /** 특정 product에 연결된 fanId → SseEmitter 맵. */
    public Map<Long, SseEmitter> getEmittersForProduct(Long productId) {
        String prefix = productId + ":";
        Map<Long, SseEmitter> result = new HashMap<>();
        for (Map.Entry<String, SseEmitter> e : emitters.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                Long fanId = Long.parseLong(e.getKey().substring(prefix.length()));
                result.put(fanId, e.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * isFull 체크와 register를 원자적으로 수행. TOCTOU 방지.
     * 상한 초과 시 SseCapacityExceededException을 던진다.
     */
    public synchronized SseEmitter registerOrReject(Long productId, Long fanId) {
        if (emitters.size() >= sseMaxEmitters) {
            throw new SseCapacityExceededException();
        }
        return register(productId, fanId);
    }

    /**
     * 5초 주기 스케줄러에서 호출.
     * 55초 초과 emitter는 Spring timeout(60s) 전에 정상 컨텍스트에서 complete() 호출.
     * onTimeout 안에서의 complete()는 Spring이 이미 async context를 닫는 중이라 효과 없음.
     */
    public void sendHeartbeat() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            String key = entry.getKey();
            Long registeredAt = registrationTimes.get(key);
            if (registeredAt != null && (now - registeredAt) > 55_000) {
                // Spring timeout 전에 스케줄러 컨텍스트에서 정상 종료 → HTTP 200 보장
                try {
                    entry.getValue().complete();
                } catch (IllegalStateException ignored) {}
                continue;
            }
            try {
                entry.getValue().send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(key);
                registrationTimes.remove(key);
            }
        }
    }

    private String key(Long productId, Long fanId) {
        return productId + ":" + fanId;
    }
}
