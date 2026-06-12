package com.fandrops.notification.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, Long> {

    List<NotificationJpaEntity> findByFanIdOrderByIdDesc(Long fanId, Pageable pageable);

    List<NotificationJpaEntity> findByFanIdAndIdLessThanOrderByIdDesc(Long fanId, Long cursorId, Pageable pageable);

    Optional<NotificationJpaEntity> findByIdAndFanId(Long id, Long fanId);
}