package com.fandrops.user.application.dto;

import com.fandrops.user.domain.AuthProvider;

// Authorization Code 방식: 클라이언트가 카카오/구글에서 받은 code를 전달
public record SocialLoginCommand(
        AuthProvider provider,
        String code
) {}