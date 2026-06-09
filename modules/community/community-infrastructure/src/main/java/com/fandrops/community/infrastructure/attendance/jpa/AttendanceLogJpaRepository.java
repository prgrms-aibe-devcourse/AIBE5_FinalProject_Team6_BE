package com.fandrops.community.infrastructure.attendance.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AttendanceLogJpaRepository extends JpaRepository<AttendanceLogJpaEntity, Long> {

    boolean existsByEventIdAndFanIdAndCheckedDate(Long eventId, Long fanId, LocalDate checkedDate);

    int countByEventIdAndFanId(Long eventId, Long fanId);
}