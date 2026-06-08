package com.fandrops.order.domain.port;

import com.fandrops.order.domain.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    List<Product> findRegularProducts(Long cursor, int size);
}
