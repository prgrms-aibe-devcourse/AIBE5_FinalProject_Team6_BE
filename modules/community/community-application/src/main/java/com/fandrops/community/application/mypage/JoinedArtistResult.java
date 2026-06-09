package com.fandrops.community.application.mypage;

import java.time.OffsetDateTime;

public record JoinedArtistResult(
        Long artistId,
        OffsetDateTime followedAt
) {}