package com.fandrops.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, Long> {

    Optional<InventoryJpaEntity> findByProductId(Long productId);

    List<InventoryJpaEntity> findByProductIdIn(List<Long> productIds);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryJpaEntity e " +
           "SET e.reservedQty = e.reservedQty + :qty, e.availableQty = e.availableQty - :qty, " +
           "e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.productId = :productId AND e.availableQty >= :qty")
    int reserveAtomic(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryJpaEntity e " +
           "SET e.totalQty = e.totalQty - :qty, e.reservedQty = e.reservedQty - :qty, " +
           "e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.productId = :productId AND e.reservedQty >= :qty AND e.totalQty >= :qty")
    int confirmAtomic(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InventoryJpaEntity e " +
           "SET e.reservedQty = e.reservedQty - :qty, e.availableQty = e.availableQty + :qty, " +
           "e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.productId = :productId AND e.reservedQty >= :qty")
    int restoreAtomic(@Param("productId") Long productId, @Param("qty") int qty);
}
