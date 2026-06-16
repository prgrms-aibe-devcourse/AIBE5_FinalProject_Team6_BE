package com.fandrops.user.application.listener;

import com.fandrops.user.application.event.AgencyApplicationApprovedEmailEvent;
import com.fandrops.user.application.event.AgencyApplicationRejectedEmailEvent;
import com.fandrops.user.application.port.EmailNotificationPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencyApprovalEmailListenerTest {

    @Mock EmailNotificationPort emailNotificationPort;
    @Mock MeterRegistry meterRegistry;
    @Mock Counter counter;
    @InjectMocks AgencyApprovalEmailListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), anyString(), anyString())).thenReturn(counter);
    }

    @Test
    @DisplayName("입점 승인 이메일 이벤트 수신 시 sendApplicationApprovedEmail 올바른 인자로 호출된다")
    void handleApproved_success_callsEmailPort() {
        var event = new AgencyApplicationApprovedEmailEvent("agency@test.com", "agency@test.com", "tmp123abc");

        listener.handleApproved(event);

        verify(emailNotificationPort).sendApplicationApprovedEmail("agency@test.com", "agency@test.com", "tmp123abc");
    }

    @Test
    @DisplayName("승인 이메일 발송 실패 시 예외를 삼키고 정상 반환된다 (fire & forget)")
    void handleApproved_emailFails_doesNotThrow() {
        doThrow(new RuntimeException("SMTP 연결 실패"))
                .when(emailNotificationPort).sendApplicationApprovedEmail(any(), any(), any());
        var event = new AgencyApplicationApprovedEmailEvent("agency@test.com", "agency@test.com", "tmp123abc");

        assertDoesNotThrow(() -> listener.handleApproved(event));
    }

    @Test
    @DisplayName("승인 이메일 발송 실패 시 fandrops_email_send_errors_total 카운터가 증가한다")
    void handleApproved_emailFails_incrementsErrorCounter() {
        doThrow(new RuntimeException("SMTP 연결 실패"))
                .when(emailNotificationPort).sendApplicationApprovedEmail(any(), any(), any());
        var event = new AgencyApplicationApprovedEmailEvent("agency@test.com", "agency@test.com", "tmp123abc");

        listener.handleApproved(event);

        verify(meterRegistry).counter("fandrops_email_send_errors_total", "type", "agency_approval");
        verify(counter).increment();
    }

    @Test
    @DisplayName("입점 반려 이메일 이벤트 수신 시 sendApplicationRejectedEmail 올바른 인자로 호출된다")
    void handleRejected_success_callsEmailPort() {
        var event = new AgencyApplicationRejectedEmailEvent("agency@test.com", "서류 미비");

        listener.handleRejected(event);

        verify(emailNotificationPort).sendApplicationRejectedEmail("agency@test.com", "서류 미비");
    }

    @Test
    @DisplayName("반려 이메일 발송 실패 시 예외를 삼키고 정상 반환된다 (fire & forget)")
    void handleRejected_emailFails_doesNotThrow() {
        doThrow(new RuntimeException("SMTP 연결 실패"))
                .when(emailNotificationPort).sendApplicationRejectedEmail(any(), any());
        var event = new AgencyApplicationRejectedEmailEvent("agency@test.com", "서류 미비");

        assertDoesNotThrow(() -> listener.handleRejected(event));
    }

    @Test
    @DisplayName("반려 이메일 발송 실패 시 fandrops_email_send_errors_total 카운터가 증가한다")
    void handleRejected_emailFails_incrementsErrorCounter() {
        doThrow(new RuntimeException("SMTP 연결 실패"))
                .when(emailNotificationPort).sendApplicationRejectedEmail(any(), any());
        var event = new AgencyApplicationRejectedEmailEvent("agency@test.com", "서류 미비");

        listener.handleRejected(event);

        verify(meterRegistry).counter("fandrops_email_send_errors_total", "type", "agency_rejection");
        verify(counter).increment();
    }

    @Test
    @DisplayName("승인 이벤트 toString()은 tempPassword를 마스킹한다")
    void approvedEvent_toString_masksTempPassword() {
        var event = new AgencyApplicationApprovedEmailEvent("a@b.com", "a@b.com", "s3cr3t");
        assertFalse(event.toString().contains("s3cr3t"));
    }

    @Test
    @DisplayName("반려 이벤트 toString()은 rejectReason을 마스킹한다")
    void rejectedEvent_toString_masksRejectReason() {
        var event = new AgencyApplicationRejectedEmailEvent("a@b.com", "내부 사유: 박○○ 불합격");
        assertFalse(event.toString().contains("내부 사유"));
    }
}
