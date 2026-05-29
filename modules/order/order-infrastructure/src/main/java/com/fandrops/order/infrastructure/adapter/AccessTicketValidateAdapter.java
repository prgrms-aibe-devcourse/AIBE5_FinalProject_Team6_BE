package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.exception.AccessTicketInvalidException;
import com.fandrops.order.domain.port.AccessTicketValidatePort;
import com.fandrops.order.domain.port.AccessTicketValidator;

/** AccessTicketValidatePort 구현체. AccessTicketValidator(payment-infrastructure 제공)로 검증한다. */
public class AccessTicketValidateAdapter implements AccessTicketValidatePort {

    private final AccessTicketValidator accessTicketValidator;

    public AccessTicketValidateAdapter(AccessTicketValidator accessTicketValidator) {
        this.accessTicketValidator = accessTicketValidator;
    }

    @Override
    public void validate(String token, Long fanId, Long productId) {
        if (!accessTicketValidator.isValid(token, fanId, productId)) {
            throw new AccessTicketInvalidException();
        }
    }
}
