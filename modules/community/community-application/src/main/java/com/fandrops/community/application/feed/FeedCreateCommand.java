package com.fandrops.community.application.feed;

import java.util.List;

public record FeedCreateCommand(
        Long artistId,
        Long artistMemberId,
        String content,
        List<String> imageUrls
) {}