package com.fandrops.user.api.dto;

public record SignUpRequest(
        String email,
        String password,
        String nickname,
        boolean termsAgreed
) {}
