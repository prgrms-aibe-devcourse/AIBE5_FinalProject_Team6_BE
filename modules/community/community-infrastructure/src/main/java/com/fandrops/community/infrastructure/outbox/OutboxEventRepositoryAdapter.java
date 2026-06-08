package com.fandrops.community.infrastructure.outbox;

import com.fandrops.community.application.port.OutboxEvent;
import com.fandrops.community.application.port.OutboxEventPort;
import com.fandrops.community.application.port.OutboxEventType;
import com.fandrops.community.infrastructure.outbox.jpa.OutboxEventJpaEntity;
import com.fandrops.community.infrastructure.outbox.jpa.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;

@Repository
public class OutboxEventRepositoryAdapter implements OutboxEventPort {

    private final OutboxEventJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventRepositoryAdapter(OutboxEventJpaRepository jpaRepository,
                                        ObjectMapper objectMapper,
                                        Clock clock) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publish(OutboxEvent event) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("outbox payload 직렬화 실패: " + event.type().eventType, e);
        }
        jpaRepository.save(new OutboxEventJpaEntity(
                null,
                event.type().eventType,
                event.type().aggregateType,
                event.aggregateId(),
                payloadJson,
                "PENDING",
                0,
                null,
                LocalDateTime.now(clock)
        ));
    }

    @Override
    public boolean existsEvent(Long aggregateId, OutboxEventType type) {
        return jpaRepository.existsByAggregateIdAndEventType(aggregateId, type.eventType);
    }
}