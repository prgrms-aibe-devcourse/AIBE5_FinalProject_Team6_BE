package com.fandrops.user.infrastructure.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotificationPortImplTest {

    @Mock JavaMailSender mailSender;

    EmailNotificationPortImpl impl;

    private static final String FROM = "noreply@fandrops.com";
    private static final String FRONTEND_URL = "https://fandrops.com";

    @BeforeEach
    void setUp() {
        impl = new EmailNotificationPortImpl(mailSender, FROM, FRONTEND_URL);
    }

    private SimpleMailMessage captureMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("sendPasswordResetEmail — 수신자·발신자·제목·링크가 올바르게 포함된다")
    void sendPasswordResetEmail_correctHeadersAndLink() {
        impl.sendPasswordResetEmail("user@test.com", "reset-token-abc");

        SimpleMailMessage msg = captureMessage();
        assertEquals(FROM, msg.getFrom());
        assertArrayEquals(new String[]{"user@test.com"}, msg.getTo());
        assertTrue(Objects.requireNonNull(msg.getSubject()).contains("비밀번호 재설정"));
        assertTrue(Objects.requireNonNull(msg.getText()).contains("/reset-password?token="));
    }

    @Test
    @DisplayName("sendPasswordResetEmail — 토큰의 특수문자(&, =)는 URL 인코딩된다")
    void sendPasswordResetEmail_specialCharsInToken_areUrlEncoded() {
        impl.sendPasswordResetEmail("user@test.com", "token&value=abc");

        SimpleMailMessage msg = captureMessage();
        String body = Objects.requireNonNull(msg.getText());
        assertFalse(body.contains("token&value=abc"), "특수문자가 인코딩 없이 포함되면 안 된다");
        assertTrue(body.contains("token%26value%3Dabc"), "& → %26, = → %3D 인코딩 확인");
    }

    @Test
    @DisplayName("sendApplicationApprovedEmail — loginId·tempPassword·로그인 링크가 본문에 포함된다")
    void sendApplicationApprovedEmail_containsLoginIdAndTempPasswordAndLink() {
        impl.sendApplicationApprovedEmail("agency@test.com", "agency_login_id", "Tmp!Pass123");

        SimpleMailMessage msg = captureMessage();
        assertArrayEquals(new String[]{"agency@test.com"}, msg.getTo());
        assertTrue(Objects.requireNonNull(msg.getSubject()).contains("승인"));
        String body = Objects.requireNonNull(msg.getText());
        assertTrue(body.contains("agency_login_id"));
        assertTrue(body.contains("Tmp!Pass123"));
        assertTrue(body.contains("/agency/login"));
    }

    @Test
    @DisplayName("sendTempPasswordResetEmail — 재발급 임시 비밀번호와 로그인 링크가 본문에 포함된다")
    void sendTempPasswordResetEmail_containsTempPasswordAndLink() {
        impl.sendTempPasswordResetEmail("agency@test.com", "agency_id", "NewTmp!456");

        SimpleMailMessage msg = captureMessage();
        assertArrayEquals(new String[]{"agency@test.com"}, msg.getTo());
        assertTrue(Objects.requireNonNull(msg.getSubject()).contains("재발급"));
        String body = Objects.requireNonNull(msg.getText());
        assertTrue(body.contains("NewTmp!456"));
        assertTrue(body.contains("/agency/login"));
    }

    @Test
    @DisplayName("sendApplicationRejectedEmail — 반려 사유가 본문에 포함된다")
    void sendApplicationRejectedEmail_containsRejectReason() {
        impl.sendApplicationRejectedEmail("agency@test.com", "서류 미비로 인한 반려");

        SimpleMailMessage msg = captureMessage();
        assertArrayEquals(new String[]{"agency@test.com"}, msg.getTo());
        assertTrue(Objects.requireNonNull(msg.getSubject()).contains("심사 결과"));
        assertTrue(Objects.requireNonNull(msg.getText()).contains("서류 미비로 인한 반려"));
    }
}
