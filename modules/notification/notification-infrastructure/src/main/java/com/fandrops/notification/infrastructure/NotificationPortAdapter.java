package com.fandrops.notification.infrastructure;

import com.fandrops.notification.domain.Notification;
import com.fandrops.notification.domain.port.NotificationPort;
import com.fandrops.notification.infrastructure.jpa.NotificationJpaEntity;
import com.fandrops.notification.infrastructure.jpa.NotificationJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationPortAdapter implements NotificationPort {

    private final NotificationJpaRepository notificationJpaRepository;

    public NotificationPortAdapter(NotificationJpaRepository notificationJpaRepository) {
        this.notificationJpaRepository = notificationJpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        return notificationJpaRepository.save(NotificationJpaEntity.from(notification)).toDomain();
    }

    @Override
    public List<Notification> findByFanId(Long fanId, Long cursorId, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        List<NotificationJpaEntity> entities = (cursorId == null)
                ? notificationJpaRepository.findByFanIdOrderByIdDesc(fanId, pageRequest)
                : notificationJpaRepository.findByFanIdAndIdLessThanOrderByIdDesc(fanId, cursorId, pageRequest);
        return entities.stream().map(NotificationJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Notification> findByIdAndFanId(Long notificationId, Long fanId) {
        return notificationJpaRepository.findByIdAndFanId(notificationId, fanId)
                .map(NotificationJpaEntity::toDomain);
    }
}