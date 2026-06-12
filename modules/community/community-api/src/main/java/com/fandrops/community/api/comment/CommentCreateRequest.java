package com.fandrops.community.api.comment;

import jakarta.validation.constraints.NotBlank;

public record CommentCreateRequest(
        @NotBlank String content,
        Long parentId
) {}