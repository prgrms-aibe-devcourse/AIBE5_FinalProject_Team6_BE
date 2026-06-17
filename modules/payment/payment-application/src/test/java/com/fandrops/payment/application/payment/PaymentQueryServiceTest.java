package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.OrderFanQueryPort;
import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderFanQueryPort orderFanQueryPort;

    @InjectMocks private PaymentQueryService service;

    private static final Long PAYMENT_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long OWNER_FAN_ID = 100L;
    private static final Long OTHER_FAN_ID = 999L;
    private static final long AMOUNT = 50_000L;

    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        pendingPayment = Payment.create(ORDER_ID, AMOUNT);
    }

    @Test
    @DisplayName("Q-1: 본인 소유 결제 조회 → PaymentDetailResult 정상 반환")
    void q1_ownerAccess_returnsDetail() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(pendingPayment));
        when(orderFanQueryPort.findFanIdByOrderId(ORDER_ID)).thenReturn(Optional.of(OWNER_FAN_ID));

        PaymentDetailResult result = service.getDetail(PAYMENT_ID, OWNER_FAN_ID);

        assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(result.getAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("Q-2: 타인 소유 결제 접근 → 404 (권한 우회 방지)")
    void q2_otherFanAccess_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(pendingPayment));
        when(orderFanQueryPort.findFanIdByOrderId(ORDER_ID)).thenReturn(Optional.of(OWNER_FAN_ID));

        assertThatThrownBy(() -> service.getDetail(PAYMENT_ID, OTHER_FAN_ID))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("Q-3: 존재하지 않는 paymentId → 404")
    void q3_paymentNotFound_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(PAYMENT_ID, OWNER_FAN_ID))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("Q-4: orders 테이블에 orderId 없음(데이터 정합성 오류) → 404로 안전하게 처리")
    void q4_orderNotFound_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(pendingPayment));
        when(orderFanQueryPort.findFanIdByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(PAYMENT_ID, OWNER_FAN_ID))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("Q-5: orderId로 본인 소유 결제 조회 → PaymentDetailResult 정상 반환")
    void q5_getDetailByOrderId_ownerAccess_returnsDetail() {
        when(orderFanQueryPort.findFanIdByOrderId(ORDER_ID)).thenReturn(Optional.of(OWNER_FAN_ID));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment));

        PaymentDetailResult result = service.getDetailByOrderId(ORDER_ID, OWNER_FAN_ID);

        assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(result.getAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("Q-6: orderId로 타인 소유 결제 접근 → 404 (권한 우회 방지)")
    void q6_getDetailByOrderId_otherFanAccess_throwsNotFound() {
        when(orderFanQueryPort.findFanIdByOrderId(ORDER_ID)).thenReturn(Optional.of(OWNER_FAN_ID));

        assertThatThrownBy(() -> service.getDetailByOrderId(ORDER_ID, OTHER_FAN_ID))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("Q-7: orderId에 해당하는 결제 없음 → 404")
    void q7_getDetailByOrderId_paymentNotFound_throwsNotFound() {
        when(orderFanQueryPort.findFanIdByOrderId(ORDER_ID)).thenReturn(Optional.of(OWNER_FAN_ID));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetailByOrderId(ORDER_ID, OWNER_FAN_ID))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}