package com.fandrops.user.api.dto;

import com.fandrops.user.domain.ArtistMember;

public record ArtistMemberResponse(
        Long id,
        Long artistId,
        String loginId,
        String memberName,
        String role,
        String profileImageUrl
) {
    public static ArtistMemberResponse from(ArtistMember member) {
        return new ArtistMemberResponse(
                member.getId(),
                member.getArtistId(),
                member.getLoginId(),
                member.getMemberName(),
                member.getRole().name(),
                member.getProfileImageUrl()
        );
    }
}
