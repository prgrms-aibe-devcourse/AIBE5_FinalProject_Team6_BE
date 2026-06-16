package com.fandrops.user.application.listener;

import com.fandrops.user.application.event.AgencyApplicationApprovedEmailEvent;
import com.fandrops.user.application.event.AgencyApplicationRejectedEmailEvent;
import com.fandrops.user.application.event.AgencyTempPasswordResetEmailEvent;
import com.fandrops.user.application.port.EmailNotificationPort;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgencyApprovalEmailListener {

    private final EmailNotificationPort emailNotificationPort;
    private final MeterRegistry meterRegistry;

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleApproved(AgencyApplicationApprovedEmailEvent event) {
        try {
            emailNotificationPort.sendApplicationApprovedEmail(
                    event.email(), event.loginId(), event.tempPassword());
        } catch (Exception e) {
            // DB 커밋 이후 실패 — 승인 자체는 유효. 계정·임시 비밀번호는 DB에 저장됨.
            // TODO: 관리자 임시 비밀번호 재발급 기능 구현 후 재시도 가능하도록 개선
            meterRegistry.counter("fandrops_email_send_errors_total", "type", "agency_approval").increment();
            log.error("입점 승인 이메일 발송 실패", e);
        }
    }

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTempPasswordReset(AgencyTempPasswordResetEmailEvent event) {
        try {
            emailNotificationPort.sendTempPasswordResetEmail(
                    event.email(), event.loginId(), event.tempPassword());
        } catch (Exception e) {
            meterRegistry.counter("fandrops_email_send_errors_total", "type", "temp_password_reset").increment();
            log.error("임시 비밀번호 재발급 이메일 발송 실패", e);
        }
    }

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRejected(AgencyApplicationRejectedEmailEvent event) {
        try {
            emailNotificationPort.sendApplicationRejectedEmail(event.email(), event.rejectReason());
        } catch (Exception e) {
            meterRegistry.counter("fandrops_email_send_errors_total", "type", "agency_rejection").increment();
            log.error("입점 반려 이메일 발송 실패", e);
        }
    }
}
