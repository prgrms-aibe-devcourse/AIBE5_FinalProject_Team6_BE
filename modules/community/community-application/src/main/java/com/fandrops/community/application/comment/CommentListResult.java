package com.fandrops.community.application.comment;

import java.util.List;

public record CommentListResult(
        List<CommentWithRepliesResult> items,
        String nextCursor,
        boolean hasMore
) {}