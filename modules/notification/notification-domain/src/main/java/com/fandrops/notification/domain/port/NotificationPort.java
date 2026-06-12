package com.fandrops.notification.domain.port;

import com.fandrops.notification.domain.Notification;
import java.util.List;
import java.util.Optional;

public interface NotificationPort {
    Notification save(Notification notification);
    List<Notification> saveAll(List<Notification> notifications);
    List<Notification> findByFanId(Long fanId, Long cursorId, int size);
    Optional<Notification> findByIdAndFanId(Long notificationId, Long fanId);
}