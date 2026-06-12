package com.fandrops.order.infrastructure.persistence;

import com.fandrops.order.domain.RestockAlertStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestockAlertJpaRepository extends JpaRepository<RestockAlertJpaEntity, Long> {

    Optional<RestockAlertJpaEntity> findByFanIdAndProductIdAndStatus(
            Long fanId, Long productId, RestockAlertStatus status);

    List<RestockAlertJpaEntity> findAllByProductIdAndStatus(
            Long productId, RestockAlertStatus status);
}
