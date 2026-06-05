package com.fandrops.community.application.vote;

import com.fandrops.community.domain.vote.GoodsVote;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record GoodsVoteResult(
        Long id,
        Long artistId,
        String title,
        OffsetDateTime endsAt,
        boolean active,
        List<GoodsVoteOptionResult> options
) {
    public static GoodsVoteResult of(GoodsVote vote,
                                     List<GoodsVoteOptionResult> options) {
        return new GoodsVoteResult(
                vote.getId(),
                vote.getArtistId(),
                vote.getTitle(),
                vote.getEndsAt().atOffset(ZoneOffset.UTC),
                vote.isActive(),
                options);
    }
}
