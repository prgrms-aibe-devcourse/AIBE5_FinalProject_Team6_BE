package com.fandrops.community.infrastructure.vote.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsVoteJpaRepository extends JpaRepository<GoodsVoteJpaEntity, Long> {

    List<GoodsVoteJpaEntity> findByArtistIdOrderByIdDesc(Long artistId, Pageable pageable);

    List<GoodsVoteJpaEntity> findByArtistIdAndIdLessThanOrderByIdDesc(Long artistId, Long cursorId, Pageable pageable);
}
