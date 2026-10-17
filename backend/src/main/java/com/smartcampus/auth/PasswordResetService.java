package com.smartcampus.auth;

import com.smartcampus.auth.dto.ForgotPasswordRequest;
import com.smartcampus.auth.dto.MessageResponse;
import com.smartcampus.auth.dto.ResetPasswordRequest;

/**
 * Service managing password reset tokens and operations.
 */
public interface PasswordResetService {

    /**
     * Business Rule (5.10 & 5.13):
     * - Rate limited at app.auth.rate-limit-per-minute (default 10) per IP and email. Fail closed on Redis error.
     * - Always returns the identical 200 message whether or not the account exists, so accounts cannot be discovered.
     * - If account exists and is active:
     *     * Creates a random 32-byte URL-safe token.
     *     * Stores ONLY its SHA-256 hash in password_reset_tokens with expires_at = now + app.auth.reset-token-minutes (default 30).
     *     * Marks user's older unused tokens as used.
     *     * Dispatches email with reset link app.auth.reset-link-base?token=... through EmailSender.
     *
     * @param request forgot password request containing email
     * @param clientIp client IP address for rate limiting
     * @return identical MessageResponse
     */
    MessageResponse forgotPassword(ForgotPasswordRequest request, String clientIp);

    /**
     * Business Rule (5.13):
     * - Look the token up by SHA-256 hash.
     * - Unknown, expired, and already used tokens all return the exact same 400 INVALID_RESET_TOKEN error code.
     * - Enforce a minimum password length of 8 characters.
     * - In ONE database transaction:
     *     * Store the new BCrypt password hash.
     *     * Set used_at = now.
     *     * Set users.password_changed_at = now.
     *     * Mark user's other tokens as used.
     * - JwtAuthFilter will subsequently reject any token issued before users.password_changed_at,
     *   ensuring all previous sessions are terminated immediately.
     *
     * @param request reset password request containing token and new password
     * @return success MessageResponse
     */
    MessageResponse resetPassword(ResetPasswordRequest request);
}
