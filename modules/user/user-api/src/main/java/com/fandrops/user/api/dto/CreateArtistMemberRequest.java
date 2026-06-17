package com.fandrops.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateArtistMemberRequest(
        @NotNull(message = "artistId는 필수입니다")
        Long artistId,

        @NotBlank(message = "loginId는 필수입니다")
        String loginId,

        @NotBlank(message = "rawPassword는 필수입니다")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
        String rawPassword,

        @NotBlank(message = "memberName은 필수입니다")
        String memberName
) {}
