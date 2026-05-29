package com.fandrops.order.infrastructure.persistence;

import com.fandrops.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA 저장소. updateStatus는 JPQL로 status만 갱신해 불필요한 items 재저장을 방지. */
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.status = :status WHERE o.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);
}
