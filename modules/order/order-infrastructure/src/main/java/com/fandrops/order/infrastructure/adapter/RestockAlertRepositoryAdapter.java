package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.RestockAlert;
import com.fandrops.order.domain.RestockAlertStatus;
import com.fandrops.order.domain.port.RestockAlertRepository;
import com.fandrops.order.infrastructure.persistence.RestockAlertJpaEntity;
import com.fandrops.order.infrastructure.persistence.RestockAlertJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RestockAlertRepositoryAdapter implements RestockAlertRepository {

    private final RestockAlertJpaRepository jpaRepository;

    @Override
    public RestockAlert save(RestockAlert alert) {
        RestockAlertJpaEntity entity = alert.getId() == null
                ? RestockAlertJpaEntity.from(alert)
                : RestockAlertJpaEntity.fromWithId(alert);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<RestockAlert> findPendingByFanIdAndProductId(Long fanId, Long productId) {
        return jpaRepository.findByFanIdAndProductIdAndStatus(fanId, productId, RestockAlertStatus.PENDING)
                .map(RestockAlertJpaEntity::toDomain);
    }

    @Override
    public List<RestockAlert> findAllPendingByProductId(Long productId) {
        return jpaRepository.findAllByProductIdAndStatus(productId, RestockAlertStatus.PENDING)
                .stream().map(RestockAlertJpaEntity::toDomain).toList();
    }
}
