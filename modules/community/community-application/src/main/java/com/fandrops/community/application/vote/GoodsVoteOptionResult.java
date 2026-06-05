package com.fandrops.community.application.vote;

public record GoodsVoteOptionResult(
        Long id,
        String label,
        String imageUrl,
        int voteCount
) {}
