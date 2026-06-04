package com.fandrops.community.infrastructure.schedule.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ArtistScheduleJpaRepository extends JpaRepository<ArtistScheduleJpaEntity, Long> {

    List<ArtistScheduleJpaEntity> findByArtistIdOrderByScheduledAtAsc(Long artistId);

    List<ArtistScheduleJpaEntity> findByArtistIdAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(
            Long artistId, LocalDateTime from);

    List<ArtistScheduleJpaEntity> findByArtistIdAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            Long artistId, LocalDateTime to);

    List<ArtistScheduleJpaEntity> findByArtistIdAndScheduledAtBetweenOrderByScheduledAtAsc(
            Long artistId, LocalDateTime from, LocalDateTime to);
}