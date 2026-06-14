package com.fandrops.user.api.dto;

import com.fandrops.user.application.dto.PresignedUploadResult;

import java.time.Instant;

public record UploadPresignedUrlResponse(
        String presignedUrl,
        String imageUrl,
        Instant expiresAt
) {
    public static UploadPresignedUrlResponse from(PresignedUploadResult result) {
        return new UploadPresignedUrlResponse(result.presignedUrl(), result.imageUrl(), result.expiresAt());
    }
}