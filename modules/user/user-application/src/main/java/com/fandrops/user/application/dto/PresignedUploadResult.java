package com.fandrops.user.application.dto;

public record PresignedUploadResult(
        String presignedUrl,
        String imageUrl
) {}
