package com.fandrops.user.application.port;

import com.fandrops.user.domain.UserRole;

public interface JwtProvider {
    String generateAccessToken(Long userId, UserRole role);
    String generateRefreshToken(Long userId);
    long getAccessTokenExpiresIn();  // 초 단위
}