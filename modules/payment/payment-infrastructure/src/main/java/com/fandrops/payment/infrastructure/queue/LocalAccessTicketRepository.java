package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.domain.queue.AccessTicketRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class LocalAccessTicketRepository implements AccessTicketRepository {

    private static final class TokenEntry {
        final String token;
        final Instant expiresAt;

        TokenEntry(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    // key: "productId:fanId" → TokenEntry
    private final ConcurrentHashMap<String, TokenEntry> store = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public LocalAccessTicketRepository(
            @Value("${fandrops.queue.access-ticket-ttl-seconds:300}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public String issue(Long fanId, Long productId) {
        String token = UUID.randomUUID().toString();
        store.put(storeKey(fanId, productId),
                new TokenEntry(token, Instant.now().plusSeconds(ttlSeconds)));
        return token;
    }

    @Override
    public boolean isValid(String token, Long fanId, Long productId) {
        String key = storeKey(fanId, productId);
        TokenEntry entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (!Instant.now().isBefore(entry.expiresAt)) {
            store.remove(key); // [P2] 만료 항목 즉시 정리
            return false;
        }
        return entry.token.equals(token);
    }

    @Override
    public void invalidate(Long fanId, Long productId) {
        store.remove(storeKey(fanId, productId));
    }

    private String storeKey(Long fanId, Long productId) {
        return productId + ":" + fanId;
    }
}