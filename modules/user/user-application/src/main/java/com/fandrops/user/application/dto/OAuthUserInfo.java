package com.fandrops.user.application.dto;

public record OAuthUserInfo(
        String providerId,
        String email,
        String nickname   // provider에 따라 null일 수 있음
) {}