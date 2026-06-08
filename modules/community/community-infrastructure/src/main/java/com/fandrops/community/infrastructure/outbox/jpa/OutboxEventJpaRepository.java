package com.fandrops.community.infrastructure.outbox.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, Long> {
    boolean existsByAggregateIdAndEventType(Long aggregateId, String eventType);
}