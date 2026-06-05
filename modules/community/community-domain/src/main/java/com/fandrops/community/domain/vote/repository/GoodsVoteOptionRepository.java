package com.fandrops.community.domain.vote.repository;

import com.fandrops.community.domain.vote.GoodsVoteOption;

import java.util.List;
import java.util.Optional;

public interface GoodsVoteOptionRepository {

    GoodsVoteOption save(GoodsVoteOption option);

    Optional<GoodsVoteOption> findById(Long id);

    List<GoodsVoteOption> findByVoteId(Long voteId);

    void incrementVoteCount(Long optionId);
}
