package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.CartItem;
import com.fandrops.order.domain.port.CartItemRepository;
import com.fandrops.order.infrastructure.persistence.CartItemJpaEntity;
import com.fandrops.order.infrastructure.persistence.CartItemJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CartItemRepositoryAdapter implements CartItemRepository {

    private final CartItemJpaRepository jpaRepository;

    @Override
    public Optional<CartItem> findById(Long id) {
        return jpaRepository.findById(id).map(CartItemJpaEntity::toDomain);
    }

    @Override
    public Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId) {
        return jpaRepository.findByCartIdAndProductId(cartId, productId)
                .map(CartItemJpaEntity::toDomain);
    }

    @Override
    public List<CartItem> findAllByCartId(Long cartId) {
        return jpaRepository.findAllByCartId(cartId).stream()
                .map(CartItemJpaEntity::toDomain)
                .toList();
    }

    @Override
    public CartItem save(CartItem item) {
        CartItemJpaEntity entity = item.getId() == null
                ? CartItemJpaEntity.from(item)
                : CartItemJpaEntity.fromWithId(item);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
