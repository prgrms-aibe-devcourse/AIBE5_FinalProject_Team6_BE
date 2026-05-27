package com.fandrops.user.application.dto;

public record SignUpCommand(
        String email,
        String password,
        String nickname,
        boolean termsAgreed
) {}