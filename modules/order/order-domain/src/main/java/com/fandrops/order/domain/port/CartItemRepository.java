package com.fandrops.order.domain.port;

import com.fandrops.order.domain.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {
    Optional<CartItem> findById(Long id);
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
    List<CartItem> findAllByCartId(Long cartId);
    CartItem save(CartItem cartItem);
    void deleteById(Long id);
}

