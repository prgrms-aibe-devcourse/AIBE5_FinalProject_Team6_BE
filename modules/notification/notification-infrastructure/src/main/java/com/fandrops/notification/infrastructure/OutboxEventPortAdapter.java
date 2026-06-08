package com.fandrops.notification.infrastructure;

import com.fandrops.notification.domain.OutboxEvent;
import com.fandrops.notification.domain.OutboxStatus;
import com.fandrops.notification.domain.port.OutboxEventPort;
import com.fandrops.notification.infrastructure.jpa.OutboxEventJpaEntity;
import com.fandrops.notification.infrastructure.jpa.OutboxEventJpaRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxEventPortAdapter implements OutboxEventPort {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    public OutboxEventPortAdapter(OutboxEventJpaRepository outboxEventJpaRepository) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
    }

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventJpaRepository.save(OutboxEventJpaEntity.from(outboxEvent)).toDomain();
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return outboxEventJpaRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, limit))
                .stream()
                .map(OutboxEventJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void update(OutboxEvent outboxEvent) {
        outboxEventJpaRepository.save(OutboxEventJpaEntity.from(outboxEvent));
    }
}