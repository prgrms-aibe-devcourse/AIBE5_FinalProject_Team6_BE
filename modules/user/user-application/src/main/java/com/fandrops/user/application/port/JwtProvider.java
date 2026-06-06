package com.fandrops.user.application.port;

import com.fandrops.user.application.dto.ParsedClaims;
import com.fandrops.user.domain.UserRole;

public interface JwtProvider {
    String generateAccessToken(Long userId, UserRole role);
    String generateRefreshToken(Long userId);
    long getAccessTokenExpiresIn();  // 초 단위
    /** 토큰 서명·만료 검증 후 클레임 반환. 유효하지 않으면 InvalidTokenException. */
    ParsedClaims parse(String token);
}