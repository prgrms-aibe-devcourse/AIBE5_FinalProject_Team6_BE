package com.fandrops.user.application.port;

// 비밀번호 재설정 이메일 발송 — 구현체는 notification 모듈
public interface EmailNotificationPort {
    void sendPasswordResetEmail(String toEmail, String resetToken);
}