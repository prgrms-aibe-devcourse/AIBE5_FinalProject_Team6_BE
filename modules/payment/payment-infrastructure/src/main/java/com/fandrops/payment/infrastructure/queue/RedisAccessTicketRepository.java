package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.domain.queue.AccessTicketRepository;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local")
public class RedisAccessTicketRepository implements AccessTicketRepository {

    // key: "access:ticket:{productId}:{fanId}" → token (UUID)
    private static final String TICKET_KEY = "access:ticket:%d:%d";

    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;

    public RedisAccessTicketRepository(
            StringRedisTemplate redisTemplate,
            @Value("${fandrops.queue.access-ticket-ttl-seconds:300}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public String issue(Long fanId, Long productId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(ticketKey(fanId, productId), token, ttlSeconds, TimeUnit.SECONDS);
        return token;
    }

    @Override
    public boolean isValid(String token, Long fanId, Long productId) {
        String stored = redisTemplate.opsForValue().get(ticketKey(fanId, productId));
        return token != null && token.equals(stored);
    }

    @Override
    public void invalidate(Long fanId, Long productId) {
        redisTemplate.delete(ticketKey(fanId, productId));
    }

    @Override
    public String get(Long fanId, Long productId) {
        return redisTemplate.opsForValue().get(ticketKey(fanId, productId));
    }

    private String ticketKey(Long fanId, Long productId) {
        return String.format(TICKET_KEY, productId, fanId);
    }
}