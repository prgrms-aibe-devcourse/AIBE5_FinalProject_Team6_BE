package com.fandrops.community.application.mypage;

import java.util.List;

public record JoinedArtistListResult(
        List<JoinedArtistResult> items,
        String nextCursor,
        boolean hasMore
) {}