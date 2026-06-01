package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.port.PasswordResetTokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class PasswordResetTokenStoreImpl implements PasswordResetTokenStore {

    private static final String KEY_PREFIX = "pwd-reset:";
    private static final String FAN_KEY_PREFIX = "pwd-reset-by-fan:";

    private final StringRedisTemplate redisTemplate;
    private final long passwordResetTtlSeconds;

    public PasswordResetTokenStoreImpl(
            StringRedisTemplate redisTemplate,
            @Value("${fandrops.security.password-reset-token-ttl-seconds:1800}") long passwordResetTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.passwordResetTtlSeconds = passwordResetTtlSeconds;
    }

    @Override
    public String generate(Long fanId) {
        String existingToken = redisTemplate.opsForValue().get(FAN_KEY_PREFIX + fanId);
        if (existingToken != null) {
            redisTemplate.delete(KEY_PREFIX + existingToken);
        }
        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(passwordResetTtlSeconds);
        redisTemplate.opsForValue().set(KEY_PREFIX + token, String.valueOf(fanId), ttl);
        redisTemplate.opsForValue().set(FAN_KEY_PREFIX + fanId, token, ttl);
        return token;
    }

    @Override
    public Optional<Long> findFanIdByToken(String token) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        return parseFanId(value);
    }

    @Override
    public void delete(String token) {
        String fanIdValue = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + token);
        if (fanIdValue != null) {
            redisTemplate.delete(FAN_KEY_PREFIX + fanIdValue);
        }
    }

    private Optional<Long> parseFanId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            log.warn("Invalid fanId payload in Redis key: {}***", KEY_PREFIX);
            return Optional.empty();
        }
    }
}