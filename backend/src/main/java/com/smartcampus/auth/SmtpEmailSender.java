package com.smartcampus.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Real email sending through SMTP. Active only when app.mail.provider=smtp.
 *
 * Requires: spring.mail.host (otherwise there is no JavaMailSender bean and the app
 * fails at startup, which is the behaviour we want), and @EnableAsync somewhere
 * (the AsyncConfig class).
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp")
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender implements EmailSender {

    // Constructor-injected by Lombok (final field), so no @Autowired is needed
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@campus.local}")
    private String fromAddress;

    @Value("${app.auth.reset-token-minutes:30}")
    private int expiryMinutes;

    /**
     * Runs on a background thread so the HTTP response time does not depend on SMTP.
     * That keeps "account exists" and "account unknown" answers equally fast.
     * Failures are logged and never thrown: forgot-password must return the same 200 either way.
     */
    @Async
    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("Smart Campus - Password Reset Request");
            message.setText(
                    "We received a request to reset your Smart Campus password.\n\n"
                            + "Use this link within " + expiryMinutes + " minutes:\n"
                            + resetLink + "\n\n"
                            + "If you did not ask for this, ignore this email. Your password stays the same.");
            mailSender.send(message);
            log.info("Password reset email sent");
        } catch (MailException e) {
            // Never log the link or the token, they are login credentials
            log.error("Password reset email failed: {}", e.getMessage());
        }
    }
}