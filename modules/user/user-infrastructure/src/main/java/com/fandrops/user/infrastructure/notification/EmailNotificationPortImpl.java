package com.fandrops.user.infrastructure.notification;

import com.fandrops.user.application.port.EmailNotificationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
                + frontendUrl + "/reset-password?token=" + URLEncoder.encode(resetToken, StandardCharsets.UTF_8)
                + "\n\n본인이 요청하지 않은 경우 이 메일을 무시하세요."
        );
        mailSender.send(message);
    }

    @Override
    public void sendApplicationApprovedEmail(String toEmail, String loginId, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("[FANDROPS] 입점 신청이 승인되었습니다");
        message.setText(
                "안녕하세요. FANDROPS 입점 신청이 승인되었습니다.\n\n"
                + "아래 정보로 로그인하신 후 반드시 비밀번호를 변경해 주세요.\n\n"
                + "로그인 ID: " + loginId + "\n"
                + "임시 비밀번호: " + tempPassword + "\n\n"
                + "로그인 페이지: " + frontendUrl + "/agency/login"
        );
        mailSender.send(message);
    }

    @Override
    public void sendApplicationRejectedEmail(String toEmail, String rejectReason) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("[FANDROPS] 입점 신청 심사 결과 안내");
        message.setText(
                "안녕하세요. FANDROPS 입점 신청 심사 결과를 안내드립니다.\n\n"
                + "아쉽게도 이번 신청은 승인되지 않았습니다.\n\n"
                + "사유: " + rejectReason + "\n\n"
                + "추가 문의는 고객센터로 연락해 주세요."
        );
        mailSender.send(message);
    }
}
