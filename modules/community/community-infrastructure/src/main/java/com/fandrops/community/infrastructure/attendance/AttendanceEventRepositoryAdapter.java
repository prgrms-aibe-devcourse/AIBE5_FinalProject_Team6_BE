package com.fandrops.community.infrastructure.attendance;

import com.fandrops.community.domain.attendance.AttendanceEvent;
import com.fandrops.community.domain.attendance.repository.AttendanceEventRepository;
import com.fandrops.community.infrastructure.attendance.jpa.AttendanceEventJpaEntity;
import com.fandrops.community.infrastructure.attendance.jpa.AttendanceEventJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class AttendanceEventRepositoryAdapter implements AttendanceEventRepository {

    private final AttendanceEventJpaRepository jpaRepository;

    public AttendanceEventRepositoryAdapter(AttendanceEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<AttendanceEvent> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<AttendanceEvent> findActiveByArtistIdAndDate(Long artistId, LocalDate date) {
        return jpaRepository.findActiveByArtistIdAndDate(artistId, date).stream()
                .map(this::toDomain)
                .toList();
    }

    private AttendanceEvent toDomain(AttendanceEventJpaEntity e) {
        return AttendanceEvent.reconstruct(
                e.getId(), e.getArtistId(), e.getStartDate(), e.getEndDate(),
                e.getRewardDesc(), e.isActive(), e.getCreatedAt());
    }
}