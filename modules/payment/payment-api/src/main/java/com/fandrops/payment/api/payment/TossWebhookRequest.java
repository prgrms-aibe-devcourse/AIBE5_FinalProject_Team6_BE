package com.fandrops.payment.api.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class TossWebhookRequest {

    private String eventType;
    private Data data;

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Data getData() { return data; }
    public void setData(Data data) { this.data = data; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Data {
        private String paymentKey;
        private String orderId;
        private String status;
        private String method;
        private long totalAmount;
        private String approvedAt;

        public String getPaymentKey() { return paymentKey; }
        public void setPaymentKey(String paymentKey) { this.paymentKey = paymentKey; }
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public long getTotalAmount() { return totalAmount; }
        public void setTotalAmount(long totalAmount) { this.totalAmount = totalAmount; }
        public String getApprovedAt() { return approvedAt; }
        public void setApprovedAt(String approvedAt) { this.approvedAt = approvedAt; }
    }
}