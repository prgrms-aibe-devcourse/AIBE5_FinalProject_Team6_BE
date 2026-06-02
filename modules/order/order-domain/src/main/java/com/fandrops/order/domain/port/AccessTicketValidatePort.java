package com.fandrops.order.domain.port;

import com.fandrops.order.domain.exception.AccessTicketInvalidException;

/** AccessTicket 유효성 검증 포트. 구현체는 AccessTicketValidateAdapter (payment-domain 연동). */
public interface AccessTicketValidatePort {

    /**
     * accessTicket이 유효하지 않으면 AccessTicketInvalidException을 던진다.
     * feat/26 머지 후 실제 구현체로 교체 예정.
     */
    void validate(String token, Long fanId, Long productId);
}
