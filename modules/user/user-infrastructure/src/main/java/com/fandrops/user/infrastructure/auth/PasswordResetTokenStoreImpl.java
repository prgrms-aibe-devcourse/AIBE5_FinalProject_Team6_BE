package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.port.PasswordResetTokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class PasswordResetTokenStoreImpl implements PasswordResetTokenStore {

    private static final String KEY_PREFIX = "pwd-reset:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public PasswordResetTokenStoreImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String generate(Long fanId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, String.valueOf(fanId), TTL);
        return token;
    }

    @Override
    public Optional<Long> findFanIdByToken(String token) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}
