package com.fandrops.user.application.dto;

public record AuthTokenResult(
        String accessToken,
        String refreshToken,  // 토큰 재발급 응답 시 null
        long expiresIn        // accessToken 만료 시간 (초)
) {}