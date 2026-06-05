package com.fandrops.order.domain.port;

import com.fandrops.order.domain.Cart;

import java.util.Optional;

public interface CartRepository {
    Optional<Cart> findByFanId(Long fanId);
    Cart save(Cart cart);
}
