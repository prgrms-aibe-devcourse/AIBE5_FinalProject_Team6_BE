package com.fandrops.order.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartJpaRepository extends JpaRepository<CartJpaEntity, Long> {
    Optional<CartJpaEntity> findByFanId(Long fanId);
}
