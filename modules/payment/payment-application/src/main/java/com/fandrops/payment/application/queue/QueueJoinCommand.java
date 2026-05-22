package com.fandrops.payment.application.queue;

public class QueueJoinCommand {

    private final Long fanId;
    private final Long productId;

    public QueueJoinCommand(Long fanId, Long productId) {
        this.fanId = fanId;
        this.productId = productId;
    }

    public Long getFanId() { return fanId; }
    public Long getProductId() { return productId; }
}