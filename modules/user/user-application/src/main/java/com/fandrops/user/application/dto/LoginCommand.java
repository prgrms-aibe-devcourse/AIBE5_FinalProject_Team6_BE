package com.fandrops.user.application.dto;

public record LoginCommand(
        String email,
        String password
) {}