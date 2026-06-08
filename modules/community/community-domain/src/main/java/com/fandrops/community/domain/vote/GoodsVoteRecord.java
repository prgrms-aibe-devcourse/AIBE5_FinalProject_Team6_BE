package com.fandrops.community.domain.vote;

import java.time.LocalDateTime;

public class GoodsVoteRecord {

    private final Long id;
    private final Long voteId;
    private final Long optionId;
    private final Long fanId;
    private final LocalDateTime votedAt;

    private GoodsVoteRecord(Long id, Long voteId, Long optionId, Long fanId, LocalDateTime votedAt) {
        this.id = id;
        this.voteId = voteId;
        this.optionId = optionId;
        this.fanId = fanId;
        this.votedAt = votedAt;
    }

    public static GoodsVoteRecord create(Long voteId, Long optionId, Long fanId, LocalDateTime votedAt) {
        return new GoodsVoteRecord(null, voteId, optionId, fanId, votedAt);
    }

    public static GoodsVoteRecord reconstruct(Long id, Long voteId, Long optionId,
                                               Long fanId, LocalDateTime votedAt) {
        return new GoodsVoteRecord(id, voteId, optionId, fanId, votedAt);
    }

    public Long getId() { return id; }
    public Long getVoteId() { return voteId; }
    public Long getOptionId() { return optionId; }
    public Long getFanId() { return fanId; }
    public LocalDateTime getVotedAt() { return votedAt; }
}
