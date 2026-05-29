package com.fandrops.order.domain.port;

/** AccessTicket 유효성 검증 outbound 포트. 구현체는 payment-infrastructure의 PaymentAccessTicketValidator. */
public interface AccessTicketValidator {

    boolean isValid(String token, Long fanId, Long productId);
}
