package com.smartcampus.auth;

/**
 * Interface for dispatching notification and password reset emails.
 */
public interface EmailSender {

    void sendPasswordResetEmail(String toEmail, String resetLink);
}
