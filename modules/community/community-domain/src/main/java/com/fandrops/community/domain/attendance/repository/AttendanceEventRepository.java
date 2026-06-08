package com.fandrops.community.domain.attendance.repository;

import com.fandrops.community.domain.attendance.AttendanceEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceEventRepository {
    Optional<AttendanceEvent> findById(Long id);
    List<AttendanceEvent> findActiveByArtistIdAndDate(Long artistId, LocalDate date);
}