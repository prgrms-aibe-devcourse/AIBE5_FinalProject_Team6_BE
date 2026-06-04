package com.fandrops.community.domain.schedule.repository;

import com.fandrops.community.domain.schedule.ArtistSchedule;

import java.time.LocalDateTime;
import java.util.List;

public interface ArtistScheduleRepository {

    // from·to null 허용 — null이면 해당 경계 조건 미적용
    List<ArtistSchedule> findByArtistIdBetween(Long artistId, LocalDateTime from, LocalDateTime to);

    ArtistSchedule save(ArtistSchedule schedule);
}