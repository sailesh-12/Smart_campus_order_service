package com.smartcampus.auth;

import com.smartcampus.auth.dto.ForgotPasswordRequest;
import com.smartcampus.auth.dto.MessageResponse;
import com.smartcampus.auth.dto.ResetPasswordRequest;
import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.util.RateLimiter;
import com.smartcampus.common.util.TokenGenerator;
import com.smartcampus.user.User;
import com.smartcampus.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    @Value("${app.auth.rate-limit-per-minute:10}")
    private int authRateLimit;

    @Value("${app.auth.reset-token-minutes:30}")
    private long resetTokenMinutes;

    @Value("${app.auth.reset-link-base:http://localhost:3000/reset-password}")
    private String resetLinkBase;

    @Override
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request, String clientIp) {
        // Enforce rate limit (fail closed on redis error)
        String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp : "unknown";
        rateLimiter.checkLimit("rate:auth:ip:", ip, authRateLimit, false);
        rateLimiter.checkLimit("rate:auth:email:", request.email().trim().toLowerCase(), authRateLimit, false);

        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail)
                .or(() -> userRepository.findByEmail(request.email().trim()));

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.isActive()) {
                Instant now = clock.instant();
                // Marks user's older unused tokens as used
                resetTokenRepository.markAllUnusedTokensAsUsed(user.getId(), now);

                // Creates a random 32-byte URL-safe token
                String rawToken = TokenGenerator.generateUrlSafeToken();
                String tokenHash = TokenGenerator.sha256Hex(rawToken);

                Instant expiresAt = now.plus(Duration.ofMinutes(resetTokenMinutes));

                PasswordResetToken resetToken = PasswordResetToken.builder()
                        .user(user)
                        .tokenHash(tokenHash)
                        .expiresAt(expiresAt)
                        .createdAt(now)
                        .build();
                resetTokenRepository.save(resetToken);

                // Dispatches email with reset link app.auth.reset-link-base?token=... through EmailSender
                String resetLink = resetLinkBase + "?token=" + rawToken;
                emailSender.sendPasswordResetEmail(user.getEmail(), resetLink);
            }
        }

        // Business Rule (5.13a): Always returns identical 200 message whether or not account exists
        return new MessageResponse("If the email is registered, a password reset link has been sent.");
    }

    @Override
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        if (request.newPassword() == null || request.newPassword().length() < 8) {
            throw new ApiException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }

        String tokenHash = TokenGenerator.sha256Hex(request.token());
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException("INVALID_RESET_TOKEN", HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));

        Instant now = clock.instant();
        if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(now)) {
            throw new ApiException("INVALID_RESET_TOKEN", HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(now);
        userRepository.save(user);

        resetToken.setUsedAt(now);
        resetTokenRepository.save(resetToken);

        resetTokenRepository.markAllUnusedTokensAsUsed(user.getId(), now);

        return new MessageResponse("Password has been reset successfully");
    }
}
