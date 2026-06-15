package com.fandrops.community.infrastructure.schedule;

import com.fandrops.community.domain.schedule.ArtistSchedule;
import com.fandrops.community.domain.schedule.ArtistScheduleType;
import com.fandrops.community.domain.schedule.repository.ArtistScheduleRepository;
import com.fandrops.community.infrastructure.schedule.jpa.ArtistScheduleJpaEntity;
import com.fandrops.community.infrastructure.schedule.jpa.ArtistScheduleJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ArtistScheduleRepositoryAdapter implements ArtistScheduleRepository {

    private final ArtistScheduleJpaRepository jpaRepository;

    public ArtistScheduleRepositoryAdapter(ArtistScheduleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ArtistSchedule> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ArtistSchedule> findByArtistIdBetween(Long artistId, LocalDateTime from, LocalDateTime to) {
        List<ArtistScheduleJpaEntity> entities;
        if (from != null && to != null) {
            entities = jpaRepository.findByArtistIdAndScheduledAtBetweenOrderByScheduledAtAsc(artistId, from, to);
        } else if (from != null) {
            entities = jpaRepository.findByArtistIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(artistId, from);
        } else if (to != null) {
            entities = jpaRepository.findByArtistIdAndScheduledAtLessThanEqualOrderByScheduledAtAsc(artistId, to);
        } else {
            entities = jpaRepository.findByArtistIdOrderByScheduledAtAsc(artistId);
        }
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public List<ArtistSchedule> findLivesByArtistId(Long artistId) {
        return jpaRepository
                .findByArtistIdAndTypeOrderByScheduledAtAsc(artistId, ArtistScheduleType.LIVE)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<ArtistSchedule> findNoticesByArtistId(Long artistId, Long cursorId, int size) {
        List<ArtistScheduleJpaEntity> entities = (cursorId == null)
                ? jpaRepository.findByArtistIdAndTypeOrderByIdDesc(
                        artistId, ArtistScheduleType.NOTICE, PageRequest.of(0, size))
                : jpaRepository.findByArtistIdAndTypeAndIdLessThanOrderByIdDesc(
                        artistId, ArtistScheduleType.NOTICE, cursorId, PageRequest.of(0, size));
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public ArtistSchedule save(ArtistSchedule schedule) {
        return toDomain(jpaRepository.save(toJpa(schedule)));
    }

    private ArtistScheduleJpaEntity toJpa(ArtistSchedule s) {
        return new ArtistScheduleJpaEntity(
                s.getId(), s.getArtistId(), s.getNoticeId(),
                s.getTitle(), s.getType(), s.getScheduledAt(), s.getLiveUrl(), s.getContent());
    }

    private ArtistSchedule toDomain(ArtistScheduleJpaEntity e) {
        return ArtistSchedule.reconstruct(
                e.getId(), e.getArtistId(), e.getNoticeId(),
                e.getTitle(), e.getType(), e.getScheduledAt(), e.getLiveUrl(), e.getContent());
    }
}