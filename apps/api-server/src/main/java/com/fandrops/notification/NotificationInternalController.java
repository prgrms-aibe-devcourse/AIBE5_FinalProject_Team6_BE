package com.fandrops.notification;

import com.fandrops.common.ApiResponse;
import com.fandrops.notification.application.PublishNotificationUseCase;
import com.fandrops.notification.application.dto.PublishNotificationCommand;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
public class NotificationInternalController {

    private final PublishNotificationUseCase publishNotificationUseCase;

    public NotificationInternalController(PublishNotificationUseCase publishNotificationUseCase) {
        this.publishNotificationUseCase = publishNotificationUseCase;
    }

    @PostMapping("/api/v1/internal/notifications/publish")
    public ResponseEntity<ApiResponse<Map<String, Long>>> publish(
            @RequestBody PublishNotificationRequest request) {

        PublishNotificationCommand command = new PublishNotificationCommand(
                request.eventType(), request.resourceId(), request.payload());
        Long eventId = publishNotificationUseCase.publish(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("eventId", eventId), traceId()));
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : java.util.UUID.randomUUID().toString();
    }
}
