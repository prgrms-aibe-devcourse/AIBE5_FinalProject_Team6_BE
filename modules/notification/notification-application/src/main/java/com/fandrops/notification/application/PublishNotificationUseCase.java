package com.fandrops.notification.application;

import com.fandrops.notification.application.dto.PublishNotificationCommand;
import com.fandrops.notification.domain.OutboxEvent;
import com.fandrops.notification.domain.port.OutboxEventPort;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishNotificationUseCase {

    private final OutboxEventPort outboxEventPort;

    public PublishNotificationUseCase(OutboxEventPort outboxEventPort) {
        this.outboxEventPort = outboxEventPort;
    }

    // AFTER_COMMIT 콜백 시점에는 EntityManagerHolder.transactionActive가 아직 true이므로
    // REQUIRED는 이미 커밋된 트랜잭션에 참여해 INSERT가 저장되지 않는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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