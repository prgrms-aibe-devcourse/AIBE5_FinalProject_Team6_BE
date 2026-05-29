package com.fandrops.user.infrastructure.notification;

import com.fandrops.user.application.port.EmailNotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationPortImpl implements EmailNotificationPort {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendUrl;

    public EmailNotificationPortImpl(
            JavaMailSender mailSender,
            @Value("${fandrops.mail.from}") String fromAddress,
            @Value("${fandrops.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("[FANDROPS] 비밀번호 재설정 안내");
        message.setText(
                "아래 링크를 클릭하여 비밀번호를 재설정해주세요 (30분 이내 유효):\n\n"
                + frontendUrl + "/reset-password?token=" + resetToken
                + "\n\n본인이 요청하지 않은 경우 이 메일을 무시하세요."
        );
        mailSender.send(message);
    }
}
