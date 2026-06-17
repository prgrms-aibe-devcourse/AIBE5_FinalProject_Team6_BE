package com.fandrops.user.application.dto;

import com.fandrops.user.domain.ArtistProfile;

import java.util.List;

public record ArtistProfileListResult(
        List<ArtistProfile> items,
        String nextCursor,
        boolean hasMore
) {}
