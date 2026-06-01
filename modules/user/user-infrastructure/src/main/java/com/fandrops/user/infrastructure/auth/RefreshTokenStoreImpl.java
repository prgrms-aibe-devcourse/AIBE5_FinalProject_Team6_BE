package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.port.RefreshTokenStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RefreshTokenStoreImpl implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final long refreshTokenExpireSeconds;

    public RefreshTokenStoreImpl(
            StringRedisTemplate redisTemplate,
            @Value("${fandrops.jwt.refresh-token-expire-seconds:604800}") long refreshTokenExpireSeconds) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
    }

    @Override
    public void save(String refreshToken, Long fanId) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + refreshToken,
                String.valueOf(fanId),
                Duration.ofSeconds(refreshTokenExpireSeconds)
        );
    }

    @Override
    public Optional<Long> findFanIdByToken(String refreshToken) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    @Override
    public void delete(String refreshToken) {
        redisTemplate.delete(KEY_PREFIX + refreshToken);
    }
}
