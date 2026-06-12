package com.fandrops.payment.application.queue;

public class QueueAdvanceResult {

    private final Long fanId;
    private final Long productId;
    private final String accessToken;

    public QueueAdvanceResult(Long fanId, Long productId, String accessToken) {
        this.fanId = fanId;
        this.productId = productId;
        this.accessToken = accessToken;
    }

    public Long getFanId() { return fanId; }
    public Long getProductId() { return productId; }
    public String getAccessToken() { return accessToken; }
}