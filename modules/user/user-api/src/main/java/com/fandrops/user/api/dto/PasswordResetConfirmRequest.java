package com.fandrops.user.api.dto;

public record PasswordResetConfirmRequest(
        String token,
        String newPassword
) {}
