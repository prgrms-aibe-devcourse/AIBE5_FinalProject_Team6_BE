package com.fandrops.order.domain.port;

import com.fandrops.order.domain.RestockAlert;
import java.util.List;
import java.util.Optional;

public interface RestockAlertRepository {
    RestockAlert save(RestockAlert alert);
    Optional<RestockAlert> findPendingByFanIdAndProductId(Long fanId, Long productId);
    List<RestockAlert> findAllPendingByProductId(Long productId);
}
