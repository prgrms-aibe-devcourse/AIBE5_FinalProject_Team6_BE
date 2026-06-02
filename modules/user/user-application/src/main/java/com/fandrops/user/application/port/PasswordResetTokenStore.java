package com.fandrops.user.application.port;

import java.util.Optional;

// Redis 기반 비밀번호 재설정 토큰 저장소 (TTL: 30분, 1회용)
public interface PasswordResetTokenStore {
    String generate(Long fanId);
    Optional<Long> findFanIdByToken(String token);
    void delete(String token);
    /**
     * GETDEL 단일 명령으로 조회+삭제를 원자적으로 수행한다.
     * confirmPasswordReset 전용 — findFanIdByToken + delete 분리 사용 시 TOCTOU 발생.
     */
    Optional<Long> getAndDelete(String token);
}
