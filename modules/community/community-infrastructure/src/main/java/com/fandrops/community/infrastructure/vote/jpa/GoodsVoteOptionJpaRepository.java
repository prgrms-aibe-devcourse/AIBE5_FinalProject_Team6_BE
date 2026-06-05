package com.fandrops.community.infrastructure.vote.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GoodsVoteOptionJpaRepository extends JpaRepository<GoodsVoteOptionJpaEntity, Long> {

    List<GoodsVoteOptionJpaEntity> findByVoteId(Long voteId);

    List<GoodsVoteOptionJpaEntity> findByVoteIdIn(List<Long> voteIds);

    // DB atomic increment — lost-update 방지 (ERD §13)
    // @Transactional 제거: 서비스 레이어 @Transactional이 트랜잭션을 제공
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GoodsVoteOptionJpaEntity o SET o.voteCount = o.voteCount + 1 WHERE o.id = :optionId")
    void incrementVoteCount(@Param("optionId") Long optionId);
}
