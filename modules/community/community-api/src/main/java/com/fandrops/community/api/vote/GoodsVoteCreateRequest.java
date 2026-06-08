package com.fandrops.community.api.vote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record GoodsVoteCreateRequest(
        @NotBlank String title,
        @NotNull OffsetDateTime endsAt,
        @NotEmpty List<OptionInput> options
) {
    public record OptionInput(@NotBlank String label, String imageUrl) {}
}
