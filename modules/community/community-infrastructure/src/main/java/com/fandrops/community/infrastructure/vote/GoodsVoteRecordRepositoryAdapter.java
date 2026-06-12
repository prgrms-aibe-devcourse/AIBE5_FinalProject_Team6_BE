package com.fandrops.community.infrastructure.vote;

import com.fandrops.community.domain.vote.GoodsVoteRecord;
import com.fandrops.community.domain.vote.repository.GoodsVoteRecordRepository;
import com.fandrops.community.infrastructure.vote.jpa.GoodsVoteRecordJpaEntity;
import com.fandrops.community.infrastructure.vote.jpa.GoodsVoteRecordJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class GoodsVoteRecordRepositoryAdapter implements GoodsVoteRecordRepository {

    private final GoodsVoteRecordJpaRepository jpaRepository;

    public GoodsVoteRecordRepositoryAdapter(GoodsVoteRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public GoodsVoteRecord save(GoodsVoteRecord record) {
        return toDomain(jpaRepository.save(toJpa(record)));
    }

    private GoodsVoteRecordJpaEntity toJpa(GoodsVoteRecord r) {
        return new GoodsVoteRecordJpaEntity(
                r.getId(), r.getVoteId(), r.getOptionId(), r.getFanId(), r.getVotedAt());
    }

    private GoodsVoteRecord toDomain(GoodsVoteRecordJpaEntity e) {
        return GoodsVoteRecord.reconstruct(
                e.getId(), e.getVoteId(), e.getOptionId(), e.getFanId(), e.getVotedAt());
    }
}
