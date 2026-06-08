package com.fandrops.notification.application;

import com.fandrops.notification.application.exception.NotificationNotFoundException;
import com.fandrops.notification.domain.Notification;
import com.fandrops.notification.domain.port.NotificationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkNotificationReadUseCase {

    private final NotificationPort notificationPort;

    public MarkNotificationReadUseCase(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @Transactional
    public void markAsRead(Long notificationId, Long fanId) {
        Notification notification = notificationPort.findByIdAndFanId(notificationId, fanId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        notification.markAsRead();
        notificationPort.save(notification);
    }
}
