package com.fandrops.user.application.port;

import com.fandrops.user.domain.UserRole;
import java.util.Optional;

// Redis 기반 Refresh 토큰 저장소
public interface RefreshTokenStore {
    void save(String refreshToken, Long userId, UserRole role);
    /** 로그아웃 전용. Rotation 경로에서는 절대 사용 금지 — {@link #getAndDelete} 사용. */
    void delete(String refreshToken);
    /**
     * GETDEL 단일 명령으로 조회+삭제를 원자적으로 수행한다.
     * Refresh Token Rotation 전용 — 동시성 환경에서 한 토큰으로 두 세션이 발급되는 TOCTOU를 방지한다.
     */
    Optional<RefreshTokenEntry> getAndDelete(String refreshToken);
}