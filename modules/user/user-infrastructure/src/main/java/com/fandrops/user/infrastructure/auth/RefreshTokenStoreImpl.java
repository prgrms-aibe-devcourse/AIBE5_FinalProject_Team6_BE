package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.port.RefreshTokenEntry;
import com.fandrops.user.application.port.RefreshTokenStore;
import com.fandrops.user.domain.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
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
    public void save(String refreshToken, Long userId, UserRole role) {
        // 저장 포맷: "userId:ROLENAME" (예: "42:ADMIN")
        String value = userId + ":" + role.name();
        redisTemplate.opsForValue().set(
                KEY_PREFIX + refreshToken,
                value,
                Duration.ofSeconds(refreshTokenExpireSeconds)
        );
    }

    @Override
    public void delete(String refreshToken) {
        redisTemplate.delete(KEY_PREFIX + refreshToken);
    }

    @Override
    public Optional<RefreshTokenEntry> getAndDelete(String refreshToken) {
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + refreshToken);
        return parseEntry(value);
    }

    private Optional<RefreshTokenEntry> parseEntry(String value) {
        if (value == null) return Optional.empty();
        int sep = value.indexOf(':');
        if (sep < 0) {
            // 이전 포맷(userId만 저장) 하위호환 — FAN으로 간주
            try {
                return Optional.of(new RefreshTokenEntry(Long.parseLong(value), UserRole.FAN));
            } catch (NumberFormatException e) {
                log.warn("Invalid refresh token payload in Redis");
                return Optional.empty();
            }
        }
        try {
            Long userId = Long.parseLong(value.substring(0, sep));
            UserRole role = UserRole.valueOf(value.substring(sep + 1));
            return Optional.of(new RefreshTokenEntry(userId, role));
        } catch (Exception e) {
            log.warn("Invalid refresh token payload in Redis");
            return Optional.empty();
        }
    }
}