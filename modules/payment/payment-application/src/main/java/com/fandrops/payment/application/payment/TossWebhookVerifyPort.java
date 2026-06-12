package com.fandrops.payment.application.payment;

public interface TossWebhookVerifyPort {

    /** rawBody와 X-Signature-256 헤더로 HMAC-SHA256 서명 검증 */
    boolean verify(String rawBody, String signatureHeader);
}