package com.fandrops.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileImageRequest(
        @NotBlank(message = "imageUrl은 필수입니다")
        String imageUrl
) {}
