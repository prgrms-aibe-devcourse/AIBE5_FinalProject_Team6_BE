package com.fandrops.user.api.dto;

import com.fandrops.user.application.dto.PresignedUploadResult;

public record UploadPresignedUrlResponse(
        String presignedUrl,
        String imageUrl
) {
    public static UploadPresignedUrlResponse from(PresignedUploadResult result) {
        return new UploadPresignedUrlResponse(result.presignedUrl(), result.imageUrl());
    }
}
