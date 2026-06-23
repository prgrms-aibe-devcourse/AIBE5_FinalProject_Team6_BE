package com.fandrops.payment.application.payment;

public class PaymentConfirmService {

    private final PaymentConfirmTxHelper txHelper;
    private final TossPaymentPort tossPaymentPort;

    public PaymentConfirmService(PaymentConfirmTxHelper txHelper,
                                 TossPaymentPort tossPaymentPort) {
        this.txHelper = txHelper;
        this.tossPaymentPort = tossPaymentPort;
    }

    public PaymentConfirmResult confirm(PaymentConfirmCommand command) {
        PrecheckResult precheck = txHelper.precheck(command);
        if (precheck.isDone()) {
            return precheck.earlyReturn();
        }

        // PG HTTP 호출 — TX 밖: 응답 대기 중 DB 커넥션 점유 없음.
        // precheck TX 커밋 후 이 지점 진입 전 다른 요청이 precheck를 통과할 수 있으나,
        // applySuccess/applyFailure의 @Version 낙관적 락이 동시 수정을 방어한다.
        TossConfirmResult pgResult = tossPaymentPort.confirm(
                command.getTossPaymentKey(), command.getAmount(), command.getOrderPaymentKey());

        if (pgResult.isSuccess()) {
            return txHelper.applySuccess(precheck.payment(), command.getTossPaymentKey(),
                    pgResult.getPaymentMethod(), pgResult.getApprovedAt());
        }
        throw txHelper.applyFailure(precheck.payment(), pgResult.getErrorCode(), pgResult.getErrorMessage());
    }
}