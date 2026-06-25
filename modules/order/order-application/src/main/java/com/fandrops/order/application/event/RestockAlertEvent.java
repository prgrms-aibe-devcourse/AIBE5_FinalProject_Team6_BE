package com.fandrops.order.application.event;

/** 재입고 발생 시 알림 구독 팬에게 발행하는 Spring 애플리케이션 이벤트. */
public class RestockAlertEvent {

    private final Long fanId;
    private final Long productId;
    private final String productName;

    public RestockAlertEvent(Long fanId, Long productId, String productName) {
        this.fanId = fanId;
        this.productId = productId;
        this.productName = productName;
    }

    public Long getFanId() { return fanId; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
}
