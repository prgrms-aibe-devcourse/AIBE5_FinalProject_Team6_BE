package com.fandrops.notification;

import com.fandrops.common.ApiResponse;
import com.fandrops.notification.application.GetNotificationsUseCase;
import com.fandrops.notification.application.MarkNotificationReadUseCase;
import com.fandrops.notification.application.dto.NotificationResult;
import com.fandrops.notification.application.exception.UnauthenticatedException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
    private final MarkNotificationReadUseCase markNotificationReadUseCase;
    private final Environment environment;

    public NotificationController(GetNotificationsUseCase getNotificationsUseCase,
                                   MarkNotificationReadUseCase markNotificationReadUseCase,
                                   Environment environment) {
        this.getNotificationsUseCase = getNotificationsUseCase;
        this.markNotificationReadUseCase = markNotificationReadUseCase;
        this.environment = environment;
    }

    @GetMapping("/api/v1/fans/me/notifications")
    public ResponseEntity<ApiResponse<List<NotificationResult>>> getNotifications(
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        List<NotificationResult> results = getNotificationsUseCase.getNotifications(fanId, cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(results, traceId()));
    }

    @PatchMapping("/api/v1/fans/me/notifications/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader,
            @PathVariable Long id) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        markNotificationReadUseCase.markAsRead(id, fanId);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId()));
    }

    private Long resolveFanId(Authentication authentication, Long header) {
        if (header != null && isLocalProfile()) {
            return header;
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return Long.parseLong(authentication.getName());
        }
        throw new UnauthenticatedException("인증 정보가 없습니다.");
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : java.util.UUID.randomUUID().toString();
    }
}
