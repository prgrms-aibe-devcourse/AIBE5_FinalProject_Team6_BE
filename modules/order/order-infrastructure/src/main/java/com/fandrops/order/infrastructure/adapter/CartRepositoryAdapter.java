package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.Cart;
import com.fandrops.order.domain.port.CartRepository;
import com.fandrops.order.infrastructure.persistence.CartJpaEntity;
import com.fandrops.order.infrastructure.persistence.CartJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CartRepositoryAdapter implements CartRepository {

    private final CartJpaRepository jpaRepository;

    @Override
    public Optional<Cart> findByFanId(Long fanId) {
        return jpaRepository.findByFanId(fanId).map(CartJpaEntity::toDomain);
    }

    @Override
    public Cart save(Cart cart) {
        return jpaRepository.save(CartJpaEntity.from(cart)).toDomain();
    }
}
