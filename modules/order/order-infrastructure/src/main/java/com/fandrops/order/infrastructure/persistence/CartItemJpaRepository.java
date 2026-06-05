package com.fandrops.order.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemJpaRepository extends JpaRepository<CartItemJpaEntity, Long> {
    Optional<CartItemJpaEntity> findByCartIdAndProductId(Long cartId, Long productId);
    List<CartItemJpaEntity> findAllByCartId(Long cartId);
}
