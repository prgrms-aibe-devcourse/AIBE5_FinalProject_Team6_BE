package com.fandrops.community.application.comment;

import java.util.List;

public record CommentWithRepliesResult(
        CommentResult comment,
        List<CommentResult> replies
) {}