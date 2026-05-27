package com.fandrops.user.application.port;

import java.util.Optional;

// Redis 기반 비밀번호 재설정 토큰 저장소 (TTL: 30분, 1회용)
public interface PasswordResetTokenStore {
    String generate(Long fanId);
    Optional<Long> findFanIdByToken(String token);
    void delete(String token);
}