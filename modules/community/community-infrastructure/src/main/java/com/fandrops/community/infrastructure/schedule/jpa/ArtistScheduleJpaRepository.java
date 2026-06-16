package com.fandrops.community.infrastructure.schedule.jpa;

import com.fandrops.community.domain.schedule.ArtistScheduleType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ArtistScheduleJpaRepository extends JpaRepository<ArtistScheduleJpaEntity, Long> {

    List<ArtistScheduleJpaEntity> findByArtistIdAndTypeOrderByScheduledAtAsc(
            Long artistId, ArtistScheduleType type);

    List<ArtistScheduleJpaEntity> findByArtistIdOrderByScheduledAtAsc(Long artistId);

    List<ArtistScheduleJpaEntity> findByArtistIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(
            Long artistId, LocalDateTime from);

    List<ArtistScheduleJpaEntity> findByArtistIdAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            Long artistId, LocalDateTime to);

    List<ArtistScheduleJpaEntity> findByArtistIdAndScheduledAtBetweenOrderByScheduledAtAsc(
            Long artistId, LocalDateTime from, LocalDateTime to);

    List<ArtistScheduleJpaEntity> findByArtistIdAndTypeOrderByIdDesc(
            Long artistId, ArtistScheduleType type, Pageable pageable);

    List<ArtistScheduleJpaEntity> findByArtistIdAndTypeAndIdLessThanOrderByIdDesc(
            Long artistId, ArtistScheduleType type, Long cursorId, Pageable pageable);
}