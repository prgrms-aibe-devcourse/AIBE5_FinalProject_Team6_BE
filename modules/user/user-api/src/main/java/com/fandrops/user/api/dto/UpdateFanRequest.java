package com.fandrops.user.api.dto;

public record UpdateFanRequest(
        String nickname,
        Boolean allowNotification
) {}
