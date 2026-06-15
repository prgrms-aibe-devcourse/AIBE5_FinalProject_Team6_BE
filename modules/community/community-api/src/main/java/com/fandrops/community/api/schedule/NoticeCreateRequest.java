package com.fandrops.community.api.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record NoticeCreateRequest(
        @NotBlank String title,
        String content,
        @Size(max = 10) List<String> imageUrls,
        OffsetDateTime scheduledAt
) {}
