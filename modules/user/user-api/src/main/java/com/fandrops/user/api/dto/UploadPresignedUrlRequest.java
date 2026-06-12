package com.fandrops.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadPresignedUrlRequest(
        @NotBlank(message = "contentType은 필수입니다")
        String contentType
) {}
