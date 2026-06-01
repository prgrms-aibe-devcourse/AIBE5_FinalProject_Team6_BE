package com.fandrops.community.application.comment;

public record CommentCreateCommand(
        Long feedId,
        Long artistId,
        Long fanId,           // null if artist member
        Long artistMemberId,  // null if fan
        Long parentId,        // null for top-level comment
        String content
) {}