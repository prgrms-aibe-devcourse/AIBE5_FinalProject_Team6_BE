package com.fandrops.user.application.port;

public interface EmailNotificationPort {
    void sendPasswordResetEmail(String toEmail, String resetToken);
    void sendApplicationApprovedEmail(String toEmail, String loginId, String tempPassword);
    void sendApplicationRejectedEmail(String toEmail, String rejectReason);
}