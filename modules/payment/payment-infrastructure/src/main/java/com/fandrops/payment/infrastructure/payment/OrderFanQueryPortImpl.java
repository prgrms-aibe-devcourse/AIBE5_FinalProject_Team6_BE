package com.fandrops.payment.infrastructure.payment;

import com.fandrops.payment.domain.payment.OrderFanQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class OrderFanQueryPortImpl implements OrderFanQueryPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Long> findFanIdByOrderId(Long orderId) {
        List<Long> result = em.createNativeQuery(
                                    "SELECT fan_id FROM orders WHERE id = :orderId", Long.class)
                .setParameter("orderId", orderId)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}