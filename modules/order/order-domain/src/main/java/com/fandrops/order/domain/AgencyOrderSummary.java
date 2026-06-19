package com.fandrops.order.domain;

import java.time.LocalDateTime;

public record AgencyOrderSummary(
        Long orderId,
        String status,
        int totalAmount,
        LocalDateTime createdAt,
        Long artistId,
        String artistName,
        String firstProductName,
        int itemCount
) {}
