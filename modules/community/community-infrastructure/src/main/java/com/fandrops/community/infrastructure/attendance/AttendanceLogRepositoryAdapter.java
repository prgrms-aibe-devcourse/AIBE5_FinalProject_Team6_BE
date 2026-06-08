package com.fandrops.community.infrastructure.attendance;

import com.fandrops.community.application.exception.AlreadyCheckedInException;
import com.fandrops.community.domain.attendance.AttendanceLog;
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
            AttendanceLogJpaEntity saved = jpaRepository.save(toEntity(log));
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(event_id, fan_id, checked_date) 동시 요청 경합 처리
            throw new AlreadyCheckedInException("오늘 이미 출석 체크를 완료했습니다.");
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