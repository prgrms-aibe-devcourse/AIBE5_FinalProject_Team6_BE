package com.fandrops.user.application.port;

import com.fandrops.user.domain.UserRole;
import java.util.Optional;

// Redis 기반 Refresh 토큰 저장소
public interface RefreshTokenStore {
    void save(String refreshToken, Long userId, UserRole role);
    /** 로그아웃 및 Rotation 삭제 단계 전용. */
    void delete(String refreshToken);
    /** 삭제 없이 조회만 수행한다. Rotation 흐름: find → issueTokens → {@link #delete} 순서로 사용. */
    Optional<RefreshTokenEntry> find(String refreshToken);
    /**
     * GETDEL 단일 명령으로 조회+삭제를 원자적으로 수행한다.
     * @deprecated Rotation 순서 수정(발급→삭제)으로 {@link #find} + {@link #delete} 조합으로 대체됨.
     */
    @Deprecated
    Optional<RefreshTokenEntry> getAndDelete(String refreshToken);
}
