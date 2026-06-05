package com.fandrops.user.api.dto;

public record LoginRequest(
        String email,
        String password
) {}
