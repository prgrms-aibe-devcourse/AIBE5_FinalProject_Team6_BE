package com.fandrops.community.infrastructure.attendance;

import com.fandrops.community.domain.attendance.AttendanceLog;
import com.fandrops.community.domain.attendance.exception.DuplicateAttendanceException;
import com.fandrops.community.domain.attendance.repository.AttendanceLogRepository;
import com.fandrops.community.infrastructure.attendance.jpa.AttendanceLogJpaEntity;
import com.fandrops.community.infrastructure.attendance.jpa.AttendanceLogJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class AttendanceLogRepositoryAdapter implements AttendanceLogRepository {

    private final AttendanceLogJpaRepository jpaRepository;

    public AttendanceLogRepositoryAdapter(AttendanceLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AttendanceLog save(AttendanceLog log) {
        try {
            // saveAndFlush: flush를 즉시 강제하여 UNIQUE 위반을 catch 범위 내에서 감지
            AttendanceLogJpaEntity saved = jpaRepository.saveAndFlush(toEntity(log));
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateAttendanceException("오늘 이미 출석 체크를 완료했습니다.");
        }
    }

    @Override
    public boolean existsByEventIdAndFanIdAndCheckedDate(Long eventId, Long fanId, LocalDate checkedDate) {
        return jpaRepository.existsByEventIdAndFanIdAndCheckedDate(eventId, fanId, checkedDate);
    }

    @Override
    public int countByEventIdAndFanId(Long eventId, Long fanId) {
        return jpaRepository.countByEventIdAndFanId(eventId, fanId);
    }

    private AttendanceLogJpaEntity toEntity(AttendanceLog log) {
        return new AttendanceLogJpaEntity(
                log.getId(), log.getEventId(), log.getFanId(),
                log.getCheckedDate(), log.getCreatedAt());
    }

    private AttendanceLog toDomain(AttendanceLogJpaEntity e) {
        return AttendanceLog.reconstruct(
                e.getId(), e.getEventId(), e.getFanId(),
                e.getCheckedDate(), e.getCreatedAt());
    }
}