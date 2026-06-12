package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.port.ProductRepository;
import com.fandrops.order.infrastructure.persistence.ProductJpaEntity;
import com.fandrops.order.infrastructure.persistence.ProductJpaRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;

@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = product.getId() == null
                ? ProductJpaEntity.from(product)
                : ProductJpaEntity.fromWithId(product);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(ProductJpaEntity::toDomain);
    }

    @Override
    public List<Product> findRegularProducts(Long cursor, int size) {
        return jpaRepository.findRegular(cursor, PageRequest.of(0, size)).stream()
                .map(ProductJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Product> findDropsProducts(Long cursor, int size) {
        return jpaRepository.findDrops(LocalDateTime.now(ZoneOffset.UTC), cursor, PageRequest.of(0, size)).stream()
                .map(ProductJpaEntity::toDomain)
                .toList();
    }
}
