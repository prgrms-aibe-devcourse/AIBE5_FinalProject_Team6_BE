package com.fandrops.community.api.feed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FeedCreateRequest(
        @NotBlank String content,
        @Size(max = 10) List<String> imageUrls
) {
    public FeedCreateRequest {
        if (imageUrls == null) {
            imageUrls = List.of();
        }
    }
}