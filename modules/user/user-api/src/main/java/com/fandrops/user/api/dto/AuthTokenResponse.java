package com.fandrops.user.api.dto;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
