package com.fandrops.user.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadPresignedUrlRequest(
        @NotBlank(message = "contentType은 필수입니다")
        String contentType,
        @NotNull(message = "contentLength는 필수입니다")
        @Min(value = 1, message = "파일 크기는 1 bytes 이상이어야 합니다")
        @Max(value = 5_242_880, message = "파일 크기는 5MB(5,242,880 bytes)를 초과할 수 없습니다")
        Long contentLength
) {}
