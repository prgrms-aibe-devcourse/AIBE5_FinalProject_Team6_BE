package com.fandrops.user.application.port;

import java.util.Optional;

// Redis 기반 Refresh 토큰 저장소
public interface RefreshTokenStore {
    void save(String refreshToken, Long fanId);
    Optional<Long> findFanIdByToken(String refreshToken);
    void delete(String refreshToken);
}