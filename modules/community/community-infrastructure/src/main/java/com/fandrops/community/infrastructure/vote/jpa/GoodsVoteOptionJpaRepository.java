package com.fandrops.community.infrastructure.vote.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GoodsVoteOptionJpaRepository extends JpaRepository<GoodsVoteOptionJpaEntity, Long> {

    List<GoodsVoteOptionJpaEntity> findByVoteId(Long voteId);

    // DB atomic increment — lost-update 방지 (ERD §13)
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE GoodsVoteOptionJpaEntity o SET o.voteCount = o.voteCount + 1 WHERE o.id = :optionId")
    void incrementVoteCount(@Param("optionId") Long optionId);
}
