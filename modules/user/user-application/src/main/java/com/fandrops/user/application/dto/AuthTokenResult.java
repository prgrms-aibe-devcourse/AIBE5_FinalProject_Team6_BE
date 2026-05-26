package com.fandrops.user.application.dto;

public record AuthTokenResult(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}