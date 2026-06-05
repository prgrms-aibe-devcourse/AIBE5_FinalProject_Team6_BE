package com.fandrops.user.api.dto;

public record RefreshResponse(
        String accessToken,
        long expiresIn
) {}
