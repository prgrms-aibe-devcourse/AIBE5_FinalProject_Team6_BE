package com.fandrops.payment.api.queue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRegistry {

    private static final long SSE_TIMEOUT_MS = 60_000L;

    // key: "productId:fanId" → SseEmitter
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long productId, Long fanId) {
        String key = key(productId, fanId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(key, emitter);
        emitter.onCompletion(() -> emitters.remove(key));
        emitter.onTimeout(() -> emitters.remove(key));
        emitter.onError(e -> emitters.remove(key));
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

    private String key(Long productId, Long fanId) {
        return productId + ":" + fanId;
    }
}