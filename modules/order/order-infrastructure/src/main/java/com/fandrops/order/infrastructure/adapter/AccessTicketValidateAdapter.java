package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.exception.AccessTicketInvalidException;
import com.fandrops.order.domain.port.AccessTicketValidatePort;
import com.fandrops.payment.domain.queue.AccessTicketRepository;

/** AccessTicketValidatePort 실제 구현체. payment-domain의 AccessTicketRepository.isValid()로 검증한다. */
public class AccessTicketValidateAdapter implements AccessTicketValidatePort {

    private final AccessTicketRepository accessTicketRepository;

    public AccessTicketValidateAdapter(AccessTicketRepository accessTicketRepository) {
        this.accessTicketRepository = accessTicketRepository;
    }

    @Override
    public void validate(String token, Long fanId, Long productId) {
        if (!accessTicketRepository.isValid(token, fanId, productId)) {
            throw new AccessTicketInvalidException();
        }
    }
}
