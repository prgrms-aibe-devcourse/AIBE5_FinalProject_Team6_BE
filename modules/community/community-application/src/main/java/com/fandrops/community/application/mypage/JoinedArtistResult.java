package com.fandrops.community.application.mypage;

import java.time.OffsetDateTime;

public record JoinedArtistResult(
        Long artistId,
        String artistName,
        String profileImageUrl,
        OffsetDateTime followedAt
) {}