package com.fandrops.user.application.dto;

public record UpdateFanCommand(
        Long fanId,
        String nickname,              // null이면 변경 안 함
        Boolean allowNotification   // null이면 변경 안 함
) {}