package com.fandrops.notification.infrastructure;

import com.fandrops.notification.application.port.FanIdResolverPort;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FanIdResolverAdapter implements FanIdResolverPort {

    private final JdbcTemplate jdbcTemplate;

    public FanIdResolverAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Long> findFanIdByOrderId(Long orderId) {
        List<Long> result = jdbcTemplate.query(
                "SELECT fan_id FROM orders WHERE id = ?",
                (rs, rowNum) -> rs.getLong("fan_id"),
                orderId
        );
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}