package com.fandrops.notification.infrastructure.jpa;

import com.fandrops.notification.domain.OutboxStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, Long> {

    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
