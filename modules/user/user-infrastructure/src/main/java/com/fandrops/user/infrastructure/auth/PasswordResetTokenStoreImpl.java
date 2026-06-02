package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.port.PasswordResetTokenStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PasswordResetTokenStoreImpl implements PasswordResetTokenStore {

    private static final String KEY_PREFIX = "pwd-reset:";
    private static final String FAN_KEY_PREFIX = "pwd-reset-by-fan:";

    /**
     * 단일 Redis 노드 전용 — ARGV[4]로 키를 동적 생성하므로 Redis Cluster 미지원.
     * MVP 단계 단일 노드 구성에서만 사용.
     *
     * KEYS[1] = FAN_KEY_PREFIX + fanId
     * KEYS[2] = KEY_PREFIX + newToken
     * ARGV[1] = fanId (string), ARGV[2] = TTL(초), ARGV[3] = newToken, ARGV[4] = KEY_PREFIX
     */
    private static final RedisScript<Long> GENERATE_SCRIPT = RedisScript.of(
            "local old = redis.call('GET', KEYS[1]) " +
            "if old ~= false then redis.call('DEL', ARGV[4] .. old) end " + // Cluster 미지원: DEL 대상 키를 KEYS 대신 ARGV로 전달
            "redis.call('SETEX', KEYS[2], tonumber(ARGV[2]), ARGV[1]) " +
            "redis.call('SETEX', KEYS[1], tonumber(ARGV[2]), ARGV[3]) " +
            "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long passwordResetTtlSeconds;

    public PasswordResetTokenStoreImpl(
            StringRedisTemplate redisTemplate,
            @Value("${fandrops.security.password-reset-token-ttl-seconds}") long passwordResetTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.passwordResetTtlSeconds = passwordResetTtlSeconds;
    }

    @Override
    public String generate(Long fanId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.execute(
                GENERATE_SCRIPT,
                List.of(FAN_KEY_PREFIX + fanId, KEY_PREFIX + token),
                String.valueOf(fanId),
                String.valueOf(passwordResetTtlSeconds),
                token,
                KEY_PREFIX);
        return token;
    }

    @Override
    public Optional<Long> findFanIdByToken(String token) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        return RedisStoreUtils.parseFanId(value);
    }

    @Override
    public void delete(String token) {
        getAndDelete(token); // 반환값 무시 — 로그아웃 전용
    }

    @Override
    public Optional<Long> getAndDelete(String token) {
        String fanIdValue = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + token);
        if (fanIdValue == null) {
            return Optional.empty();
        }
        redisTemplate.delete(FAN_KEY_PREFIX + fanIdValue);
        return RedisStoreUtils.parseFanId(fanIdValue);
    }
}
