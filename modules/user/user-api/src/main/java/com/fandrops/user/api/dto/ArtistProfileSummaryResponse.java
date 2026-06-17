package com.fandrops.user.api.dto;

import com.fandrops.user.domain.ArtistProfile;

public record ArtistProfileSummaryResponse(
        Long id,
        String name,
        String profileImageUrl,
        long fanCount
) {
    public static ArtistProfileSummaryResponse from(ArtistProfile profile) {
        return new ArtistProfileSummaryResponse(
                profile.getId(),
                profile.getName(),
                profile.getProfileImageUrl(),
                profile.getFanCount()
        );
    }
}
