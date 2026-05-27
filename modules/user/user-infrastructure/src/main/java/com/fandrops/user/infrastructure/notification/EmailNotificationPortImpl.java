package com.fandrops.user.infrastructure.notification;

import com.fandrops.user.application.port.EmailNotificationPort;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationPortImpl implements EmailNotificationPort {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {}
}
