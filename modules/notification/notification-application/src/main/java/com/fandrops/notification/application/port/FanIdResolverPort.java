package com.fandrops.notification.application.port;

import java.util.Optional;

public interface FanIdResolverPort {
    Optional<Long> findFanIdByOrderId(Long orderId);
}