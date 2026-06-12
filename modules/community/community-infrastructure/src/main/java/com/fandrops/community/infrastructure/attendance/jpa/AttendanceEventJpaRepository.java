package com.fandrops.community.infrastructure.attendance.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceEventJpaRepository extends JpaRepository<AttendanceEventJpaEntity, Long> {

    @Query("SELECT e FROM AttendanceEventJpaEntity e " +
           "WHERE e.artistId = :artistId AND e.active = true " +
           "AND e.startDate <= :date AND e.endDate >= :date")
    List<AttendanceEventJpaEntity> findActiveByArtistIdAndDate(
            @Param("artistId") Long artistId,
            @Param("date") LocalDate date);
}