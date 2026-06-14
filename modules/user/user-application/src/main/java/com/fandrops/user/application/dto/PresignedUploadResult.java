package com.fandrops.user.application.dto;

import java.time.Instant;

public record PresignedUploadResult(
        String presignedUrl,
        String imageUrl,
        Instant expiresAt
) {}
