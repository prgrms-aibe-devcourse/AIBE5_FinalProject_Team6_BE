package com.fandrops.payment.application.payment;

import java.time.Instant;

public class TossConfirmResult {

    private final boolean success;
    private final String paymentMethod;
    private final Instant approvedAt;
    private final String errorCode;
    private final String errorMessage;

    private TossConfirmResult(boolean success, String paymentMethod, Instant approvedAt,
                              String errorCode, String errorMessage) {
        this.success = success;
        this.paymentMethod = paymentMethod;
        this.approvedAt = approvedAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static TossConfirmResult success(String paymentMethod, Instant approvedAt) {
        return new TossConfirmResult(true, paymentMethod, approvedAt, null, null);
    }

    public static TossConfirmResult failure(String errorCode, String errorMessage) {
        return new TossConfirmResult(false, null, null, errorCode, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public String getPaymentMethod() { return paymentMethod; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}