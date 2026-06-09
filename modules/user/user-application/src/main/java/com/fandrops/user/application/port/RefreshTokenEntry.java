package com.fandrops.user.application.port;

import com.fandrops.user.domain.UserRole;

public record RefreshTokenEntry(Long userId, UserRole role) {}
