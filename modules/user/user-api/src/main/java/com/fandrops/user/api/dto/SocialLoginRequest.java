package com.fandrops.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(@NotBlank String code) {}