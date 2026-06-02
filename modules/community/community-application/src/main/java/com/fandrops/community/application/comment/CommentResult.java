package com.fandrops.community.application.comment;

import java.time.OffsetDateTime;

public record CommentResult(
        Long id,
        Long feedId,
        Long fanId,
        Long artistMemberId,
        Long parentId,
        String content,
        OffsetDateTime createdAt
) {}