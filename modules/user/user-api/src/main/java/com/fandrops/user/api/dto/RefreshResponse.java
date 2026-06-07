package com.fandrops.user.api.dto;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
