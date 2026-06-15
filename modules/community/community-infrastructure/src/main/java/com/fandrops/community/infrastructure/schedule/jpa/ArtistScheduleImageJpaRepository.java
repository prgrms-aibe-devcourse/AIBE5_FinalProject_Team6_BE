package com.fandrops.community.infrastructure.schedule.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistScheduleImageJpaRepository
        extends JpaRepository<ArtistScheduleImageJpaEntity, Long> {

    List<ArtistScheduleImageJpaEntity> findByScheduleIdOrderBySortOrder(Long scheduleId);

    List<ArtistScheduleImageJpaEntity> findByScheduleIdInOrderBySortOrder(List<Long> scheduleIds);
}
