package com.fandrops.notification.application;

import com.fandrops.notification.application.dto.PublishNotificationCommand;
import com.fandrops.notification.domain.OutboxEvent;
import com.fandrops.notification.domain.port.OutboxEventPort;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishNotificationUseCase {

    private final OutboxEventPort outboxEventPort;

    public PublishNotificationUseCase(OutboxEventPort outboxEventPort) {
        this.outboxEventPort = outboxEventPort;
    }

    @Transactional
    public Long publish(PublishNotificationCommand command) {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(command.getEventType())
                .resourceId(command.getResourceId())
                .payload(command.getPayload())
                .createdAt(Instant.now())
                .build();

        return outboxEventPort.save(event).getId();
    }
}