package com.fandrops.community.domain.schedule.repository;

import com.fandrops.community.domain.schedule.ArtistSchedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArtistScheduleRepository {

    Optional<ArtistSchedule> findById(Long id);

    // from·to null 허용 — null이면 해당 경계 조건 미적용
    List<ArtistSchedule> findByArtistIdBetween(Long artistId, LocalDateTime from, LocalDateTime to);

    List<ArtistSchedule> findLivesByArtistId(Long artistId);

    // cursor 기반 NOTICE 목록 조회 — cursorId null 이면 첫 페이지
    List<ArtistSchedule> findNoticesByArtistId(Long artistId, Long cursorId, int size);

    ArtistSchedule save(ArtistSchedule schedule);
}