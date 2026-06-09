package com.fandrops.community.domain.attendance.repository;

import com.fandrops.community.domain.attendance.AttendanceLog;

import java.time.LocalDate;

public interface AttendanceLogRepository {
    AttendanceLog save(AttendanceLog log);
    boolean existsByEventIdAndFanIdAndCheckedDate(Long eventId, Long fanId, LocalDate checkedDate);
    int countByEventIdAndFanId(Long eventId, Long fanId);
}