package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.OrderFanQueryPort;
import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final OrderFanQueryPort orderFanQueryPort;

    public PaymentQueryService(PaymentRepository paymentRepository,
                               OrderFanQueryPort orderFanQueryPort) {
        this.paymentRepository = paymentRepository;
        this.orderFanQueryPort = orderFanQueryPort;
    }

    @Transactional(readOnly = true)
    public PaymentDetailResult getDetail(Long paymentId, Long fanId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        Long ownerFanId = orderFanQueryPort.findFanIdByOrderId(payment.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (!ownerFanId.equals(fanId)) {
            throw new PaymentNotFoundException(paymentId);
        }

        return PaymentDetailResult.from(payment);
    }
}