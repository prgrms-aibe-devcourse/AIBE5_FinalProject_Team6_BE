package com.fandrops.notification.application;

import com.fandrops.notification.application.dto.NotificationResult;
import com.fandrops.notification.domain.port.NotificationPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetNotificationsUseCase {

    private static final int DEFAULT_SIZE = 20;

    private final NotificationPort notificationPort;

    public GetNotificationsUseCase(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @Transactional(readOnly = true)
    public List<NotificationResult> getNotifications(Long fanId, Long cursorId, Integer size) {
        int pageSize = (size != null && size > 0) ? size : DEFAULT_SIZE;
        return notificationPort.findByFanId(fanId, cursorId, pageSize)
                .stream()
                .map(NotificationResult::from)
                .toList();
    }
}
