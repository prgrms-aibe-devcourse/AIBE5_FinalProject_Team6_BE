package com.fandrops.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, Long> {

    Optional<InventoryJpaEntity> findByProductId(Long productId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryJpaEntity e " +
           "SET e.reservedQty = e.reservedQty + :qty, e.availableQty = e.availableQty - :qty " +
           "WHERE e.productId = :productId AND e.availableQty >= :qty")
    int reserveAtomic(@Param("productId") Long productId, @Param("qty") int qty);
}
