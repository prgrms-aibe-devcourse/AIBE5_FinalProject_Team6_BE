package com.fandrops.notification.application;

import com.fandrops.notification.application.dto.PublishNotificationCommand;
import com.fandrops.notification.domain.OutboxEvent;
import com.fandrops.notification.domain.OutboxStatus;
import com.fandrops.notification.domain.port.OutboxEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublishNotificationUseCaseTest {

    @Mock
    private OutboxEventPort outboxEventPort;

    private PublishNotificationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublishNotificationUseCase(outboxEventPort);
    }

    @Test
    @DisplayName("정상 발행 — OutboxEvent 저장 후 ID 반환")
    void publish_savesOutboxEventAndReturnsId() {
        OutboxEvent saved = OutboxEvent.builder()
                .id(1L)
                .eventType("PAYMENT_SUCCESS")
                .resourceId(100L)
                .payload("{\"orderId\":100}")
                .createdAt(Instant.now())
                .build();
        when(outboxEventPort.save(any())).thenReturn(saved);

        PublishNotificationCommand command = new PublishNotificationCommand(
                "PAYMENT_SUCCESS", 100L, "{\"orderId\":100}");
        Long eventId = useCase.publish(command);

        assertEquals(1L, eventId);
    }

    @Test
    @DisplayName("저장된 OutboxEvent의 eventType, resourceId, payload 검증")
    void publish_correctFieldsSavedToOutbox() {
        OutboxEvent saved = OutboxEvent.builder()
                .id(2L)
                .eventType("PAYMENT_FAILED")
                .resourceId(200L)
                .payload("{\"orderId\":200}")
                .createdAt(Instant.now())
                .build();
        when(outboxEventPort.save(any())).thenReturn(saved);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        useCase.publish(new PublishNotificationCommand("PAYMENT_FAILED", 200L, "{\"orderId\":200}"));

        verify(outboxEventPort).save(captor.capture());
        OutboxEvent captured = captor.getValue();
        assertEquals("PAYMENT_FAILED", captured.getEventType());
        assertEquals(200L, captured.getResourceId());
        assertEquals("{\"orderId\":200}", captured.getPayload());
        assertEquals(OutboxStatus.PENDING, captured.getStatus());
    }
}
