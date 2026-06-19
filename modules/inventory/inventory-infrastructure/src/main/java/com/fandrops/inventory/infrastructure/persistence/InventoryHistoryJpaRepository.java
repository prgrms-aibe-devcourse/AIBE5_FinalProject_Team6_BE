package com.fandrops.inventory.infrastructure.persistence;

import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryRefType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryHistoryJpaRepository extends JpaRepository<InventoryHistoryJpaEntity, Long> {

    boolean existsByReferenceIdAndRefTypeAndChangeType(Long referenceId, InventoryRefType refType, InventoryChangeType changeType);

    @Query(value = """
            SELECT ih.id, ih.inventory_id, ih.change_type, ih.qty_delta, ih.qty_before, ih.qty_after, ih.reference_id, ih.ref_type, ih.created_at
            FROM inventory_history ih
            JOIN inventory inv ON inv.id = ih.inventory_id
            JOIN product p ON p.id = inv.product_id
            JOIN artist_profile ap ON ap.id = p.artist_id
            WHERE ap.agency_id = :agencyId
              AND (:artistId IS NULL OR p.artist_id = :artistId)
              AND (:productId IS NULL OR inv.product_id = :productId)
              AND (:cursor IS NULL OR ih.id < :cursor)
            ORDER BY ih.id DESC
            LIMIT :size
            """, nativeQuery = true)
    List<InventoryHistoryJpaEntity> findByAgencyNative(
            @Param("agencyId") Long agencyId,
            @Param("artistId") Long artistId,
            @Param("productId") Long productId,
            @Param("cursor") Long cursor,
            @Param("size") int size);
}
