package com.fandrops.user.api.dto;

public record UpdateArtistProfileRequest(
        String bio,
        String profileImageUrl,
        String instagramUrl,
        String youtubeUrl,
        String twitterUrl,
        String officialUrl
) {}
