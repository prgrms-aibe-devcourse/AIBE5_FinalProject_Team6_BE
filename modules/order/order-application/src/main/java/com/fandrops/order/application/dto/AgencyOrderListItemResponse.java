package com.fandrops.order.application.dto;

import com.fandrops.order.domain.AgencyOrderSummary;
import java.time.LocalDateTime;

public record AgencyOrderListItemResponse(
        Long orderId,
        String status,
        int totalAmount,
        LocalDateTime createdAt,
        String productSummary,
        Long artistId,
        String artistName
) {
    public static AgencyOrderListItemResponse from(AgencyOrderSummary s) {
        String productSummary = s.itemCount() > 1
                ? s.firstProductName() + " 외 " + (s.itemCount() - 1) + "건"
                : s.firstProductName();
        return new AgencyOrderListItemResponse(
                s.orderId(), s.status(), s.totalAmount(), s.createdAt(),
                productSummary, s.artistId(), s.artistName());
    }
}
