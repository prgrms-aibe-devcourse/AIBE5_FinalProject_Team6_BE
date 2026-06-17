package com.fandrops.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateArtistMemberRequest(
        @NotBlank(message = "memberName은 필수입니다")
        String memberName
) {}