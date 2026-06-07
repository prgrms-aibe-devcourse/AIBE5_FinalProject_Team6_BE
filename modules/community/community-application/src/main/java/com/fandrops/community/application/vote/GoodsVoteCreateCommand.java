package com.fandrops.community.application.vote;

import java.time.OffsetDateTime;
import java.util.List;

public record GoodsVoteCreateCommand(
        Long artistId,
        String title,
        OffsetDateTime endsAt,
        List<OptionInput> options
) {
    public record OptionInput(String label, String imageUrl) {}
}
