package com.fandrops.user.api.dto;

import com.fandrops.user.domain.ArtistMember;

public record ArtistMemberSummaryResponse(
        Long id,
        String memberName,
        String profileImageUrl
) {
    public static ArtistMemberSummaryResponse from(ArtistMember member) {
        return new ArtistMemberSummaryResponse(
                member.getId(),
                member.getMemberName(),
                member.getProfileImageUrl()
        );
    }
}
