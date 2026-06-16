package com.fandrops.user.application.dto;

public record CreateArtistMemberCommand(
        Long artistId,
        String loginId,
        String rawPassword,
        String memberName
) {}
