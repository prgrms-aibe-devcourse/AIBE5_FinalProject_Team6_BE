package com.fandrops.community.infrastructure.vote;

import com.fandrops.community.domain.vote.GoodsVote;
import com.fandrops.community.domain.vote.repository.GoodsVoteRepository;
import com.fandrops.community.infrastructure.vote.jpa.GoodsVoteJpaEntity;
import com.fandrops.community.infrastructure.vote.jpa.GoodsVoteJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class GoodsVoteRepositoryAdapter implements GoodsVoteRepository {

    private final GoodsVoteJpaRepository jpaRepository;

    public GoodsVoteRepositoryAdapter(GoodsVoteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public GoodsVote save(GoodsVote vote) {
        return toDomain(jpaRepository.save(toJpa(vote)));
    }

    @Override
    public Optional<GoodsVote> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<GoodsVote> findByArtistId(Long artistId, Long cursorId, int size) {
        PageRequest page = PageRequest.of(0, size);
        List<GoodsVoteJpaEntity> entities = (cursorId == null)
                ? jpaRepository.findByArtistIdOrderByIdDesc(artistId, page)
                : jpaRepository.findByArtistIdAndIdLessThanOrderByIdDesc(artistId, cursorId, page);
        return entities.stream().map(this::toDomain).toList();
    }

    private GoodsVoteJpaEntity toJpa(GoodsVote v) {
        return new GoodsVoteJpaEntity(
                v.getId(), v.getArtistId(), v.getTitle(),
                v.getEndsAt(), v.isActive(), v.getCreatedAt());
    }

    private GoodsVote toDomain(GoodsVoteJpaEntity e) {
        return GoodsVote.reconstruct(
                e.getId(), e.getArtistId(), e.getTitle(),
                e.getEndsAt(), e.isActive(), e.getCreatedAt());
    }
}
