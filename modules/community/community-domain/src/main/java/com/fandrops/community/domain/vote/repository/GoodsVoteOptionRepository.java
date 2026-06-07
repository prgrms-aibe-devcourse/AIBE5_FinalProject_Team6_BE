package com.fandrops.community.domain.vote.repository;

import com.fandrops.community.domain.vote.GoodsVoteOption;

import java.util.List;
import java.util.Optional;

public interface GoodsVoteOptionRepository {

    GoodsVoteOption save(GoodsVoteOption option);

    Optional<GoodsVoteOption> findById(Long id);

    List<GoodsVoteOption> findByVoteId(Long voteId);

    // N+1 방지: 여러 voteId의 옵션을 단일 IN 쿼리로 조회
    List<GoodsVoteOption> findByVoteIdIn(List<Long> voteIds);

    void incrementVoteCount(Long optionId);
}
