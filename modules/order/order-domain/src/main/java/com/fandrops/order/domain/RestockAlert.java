package com.fandrops.order.domain;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class RestockAlert {

    private Long id;
    private Long fanId;
    private Long productId;
    private RestockAlertStatus status;
    private LocalDateTime createdAt;

    private RestockAlert() {}

    public static RestockAlert create(Long fanId, Long productId) {
        RestockAlert alert = new RestockAlert();
        alert.fanId = fanId;
        alert.productId = productId;
        alert.status = RestockAlertStatus.PENDING;
        return alert;
    }

    public static RestockAlert of(Long id, Long fanId, Long productId,
                                  RestockAlertStatus status, LocalDateTime createdAt) {
        RestockAlert alert = new RestockAlert();
        alert.id = id;
        alert.fanId = fanId;
        alert.productId = productId;
        alert.status = status;
        alert.createdAt = createdAt;
        return alert;
    }

    public void cancel()   { this.status = RestockAlertStatus.CANCELLED; }
    public void markSent() { this.status = RestockAlertStatus.SENT; }
}
