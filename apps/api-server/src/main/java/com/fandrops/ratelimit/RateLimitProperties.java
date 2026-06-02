package com.fandrops.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fandrops.ratelimit")
public class RateLimitProperties {

    private int orderPerMinute = 10;
    private int queueJoinPerMinute = 5;
    private int paymentPerMinute = 10;
    private int sseMaxEmitters = 2000;
    private long windowMs = 60_000L;

    public int getOrderPerMinute() { return orderPerMinute; }
    public void setOrderPerMinute(int orderPerMinute) { this.orderPerMinute = orderPerMinute; }

    public int getQueueJoinPerMinute() { return queueJoinPerMinute; }
    public void setQueueJoinPerMinute(int queueJoinPerMinute) { this.queueJoinPerMinute = queueJoinPerMinute; }

    public int getPaymentPerMinute() { return paymentPerMinute; }
    public void setPaymentPerMinute(int paymentPerMinute) { this.paymentPerMinute = paymentPerMinute; }

    public int getSseMaxEmitters() { return sseMaxEmitters; }
    public void setSseMaxEmitters(int sseMaxEmitters) { this.sseMaxEmitters = sseMaxEmitters; }

    public long getWindowMs() { return windowMs; }
    public void setWindowMs(long windowMs) { this.windowMs = windowMs; }
}