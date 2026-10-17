package com.smartcampus.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development implementation of EmailSender that logs email links instead of sending real emails.
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "log", matchIfMissing = true)
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("[MOCK EMAIL] Password reset email to: {} | Reset link: {}", toEmail, resetLink);
    }
}
