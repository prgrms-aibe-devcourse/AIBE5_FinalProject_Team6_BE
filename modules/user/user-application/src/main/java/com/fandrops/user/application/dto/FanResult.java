package com.fandrops.user.application.dto;

import com.fandrops.user.domain.Fan;

import java.time.LocalDateTime;

public record FanResult(
        Long fanId,
        String email,
        String nickname,
        boolean isAllowNotification,
        LocalDateTime createdAt
) {
    public static FanResult from(Fan fan) {
        return new FanResult(
                fan.getId(),
                fan.getEmail(),
                fan.getNickname(),
                fan.isAllowNotification(),
                fan.getCreatedAt()
        );
    }
}