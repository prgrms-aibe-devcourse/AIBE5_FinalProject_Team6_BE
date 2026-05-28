package com.fandrops.order.domain.exception;

/** 대기열 AccessTicket이 유효하지 않을 때. HTTP 403 INVALID_QUEUE_TICKET. */
public class AccessTicketInvalidException extends RuntimeException {

    public AccessTicketInvalidException() {
        super("유효하지 않은 대기열 티켓입니다. 다시 입장해 주세요.");
    }
}
