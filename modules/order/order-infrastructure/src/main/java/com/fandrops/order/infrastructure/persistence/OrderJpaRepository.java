package com.fandrops.order.infrastructure.persistence;

import com.fandrops.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA 저장소. updateStatus는 JPQL로 status만 갱신해 불필요한 items 재저장을 방지. */
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.status = :status WHERE o.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);

    List<OrderEntity> findByStatusAndUpdatedAtBefore(OrderStatus status, LocalDateTime cutoff);

    @Query("SELECT o FROM OrderEntity o WHERE o.fanId = :fanId AND (:cursor IS NULL OR o.id < :cursor) ORDER BY o.id DESC LIMIT :size")
    List<OrderEntity> findByFanIdCursor(@Param("fanId") Long fanId, @Param("cursor") Long cursor, @Param("size") int size);

    @Query(value = """
            SELECT DISTINCT o.id FROM orders o
            JOIN order_item oi ON oi.order_id = o.id
            JOIN product p ON p.id = oi.product_id
            JOIN artist_profile ap ON ap.id = p.artist_id
            WHERE ap.agency_id = :agencyId
              AND (:artistId IS NULL OR p.artist_id = :artistId)
              AND (:cursor IS NULL OR o.id < :cursor)
            ORDER BY o.id DESC
            LIMIT :size
            """, nativeQuery = true)
    List<Long> findOrderIdsByAgency(@Param("agencyId") Long agencyId, @Param("artistId") Long artistId,
                                    @Param("cursor") Long cursor, @Param("size") int size);

    List<OrderEntity> findByIdInOrderByIdDesc(List<Long> ids);

    long countByStatus(OrderStatus status);
}
