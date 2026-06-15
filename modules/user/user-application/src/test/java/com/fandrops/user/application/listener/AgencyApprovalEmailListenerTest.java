package com.fandrops.user.application.listener;

import com.fandrops.user.application.event.AgencyApplicationApprovedEmailEvent;
import com.fandrops.user.application.event.AgencyApplicationRejectedEmailEvent;
import com.fandrops.user.application.port.EmailNotificationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencyApprovalEmailListenerTest {

    @Mock EmailNotificationPort emailNotificationPort;
    @InjectMocks AgencyApprovalEmailListener listener;

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
}
