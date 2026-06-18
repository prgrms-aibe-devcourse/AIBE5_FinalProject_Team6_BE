package com.fandrops.user.api.dto;

import com.fandrops.user.domain.ArtistProfile;

import java.time.LocalDateTime;

public record ArtistProfileResponse(
        Long id,
        String name,
        long fanCount,
        LocalDateTime joinedAt,
        String profileImageUrl,
        String coverImageUrl,
        String bio,
        String homepageUrl,
        String youtubeUrl,
        String instagramUrl
) {
    public static ArtistProfileResponse from(ArtistProfile profile) {
        return new ArtistProfileResponse(
                profile.getId(),
                profile.getName(),
                profile.getFanCount(),
                profile.getJoinedAt(),
                profile.getProfileImageUrl(),
                profile.getCoverImageUrl(),
                profile.getBio(),
                profile.getHomepageUrl(),
                profile.getYoutubeUrl(),
                profile.getInstagramUrl()
        );
    }
}