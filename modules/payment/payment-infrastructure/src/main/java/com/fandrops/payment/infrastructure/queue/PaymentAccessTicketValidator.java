package com.fandrops.payment.infrastructure.queue;

import com.fandrops.order.domain.port.AccessTicketValidator;
import com.fandrops.payment.domain.queue.AccessTicketRepository;

public class PaymentAccessTicketValidator implements AccessTicketValidator {

    private final AccessTicketRepository accessTicketRepository;

    public PaymentAccessTicketValidator(AccessTicketRepository accessTicketRepository) {
        this.accessTicketRepository = accessTicketRepository;
    }

    @Override
    public boolean isValid(String token, Long fanId, Long productId) {
        return accessTicketRepository.isValid(token, fanId, productId);
    }
}