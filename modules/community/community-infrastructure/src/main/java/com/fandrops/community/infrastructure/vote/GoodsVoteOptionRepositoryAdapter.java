package com.fandrops.community.infrastructure.vote;

import com.fandrops.community.domain.vote.GoodsVoteOption;
import com.fandrops.community.domain.vote.repository.GoodsVoteOptionRepository;
import com.fandrops.community.infrastructure.vote.jpa.GoodsVoteOptionJpaEntity;
import com.fandrops.community.infrastructure.vote.jpa.GoodsVoteOptionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class GoodsVoteOptionRepositoryAdapter implements GoodsVoteOptionRepository {

    private final GoodsVoteOptionJpaRepository jpaRepository;

    public GoodsVoteOptionRepositoryAdapter(GoodsVoteOptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public GoodsVoteOption save(GoodsVoteOption option) {
        return toDomain(jpaRepository.save(toJpa(option)));
    }

    @Override
    public Optional<GoodsVoteOption> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<GoodsVoteOption> findByVoteId(Long voteId) {
        return jpaRepository.findByVoteId(voteId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<GoodsVoteOption> findByVoteIdIn(List<Long> voteIds) {
        return jpaRepository.findByVoteIdIn(voteIds).stream().map(this::toDomain).toList();
    }

    @Override
    public void incrementVoteCount(Long optionId) {
        jpaRepository.incrementVoteCount(optionId);
    }

    private GoodsVoteOptionJpaEntity toJpa(GoodsVoteOption o) {
        return new GoodsVoteOptionJpaEntity(
                o.getId(), o.getVoteId(), o.getLabel(), o.getImageUrl(), o.getVoteCount());
    }

    private GoodsVoteOption toDomain(GoodsVoteOptionJpaEntity e) {
        return GoodsVoteOption.reconstruct(
                e.getId(), e.getVoteId(), e.getLabel(), e.getImageUrl(), e.getVoteCount());
    }
}
