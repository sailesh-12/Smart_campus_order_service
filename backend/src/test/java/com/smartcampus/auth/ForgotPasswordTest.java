package com.smartcampus.auth;

import com.smartcampus.auth.dto.ForgotPasswordRequest;
import com.smartcampus.auth.dto.MessageResponse;
import com.smartcampus.auth.dto.ResetPasswordRequest;
import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.security.JwtService;
import com.smartcampus.common.util.RateLimiter;
import com.smartcampus.common.util.TokenGenerator;
import com.smartcampus.user.Role;
import com.smartcampus.user.User;
import com.smartcampus.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ForgotPasswordTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository resetTokenRepository;
    private EmailSender emailSender;
    private PasswordEncoder passwordEncoder;
    private RateLimiter rateLimiter;
    private Clock clock;
    private PasswordResetServiceImpl passwordResetService;

    private final Instant now = Instant.parse("2026-10-17T12:00:00Z");

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        resetTokenRepository = mock(PasswordResetTokenRepository.class);
        emailSender = mock(EmailSender.class);
        passwordEncoder = mock(PasswordEncoder.class);
        rateLimiter = mock(RateLimiter.class);
        clock = Clock.fixed(now, ZoneId.of("UTC"));

        passwordResetService = new PasswordResetServiceImpl(
                userRepository,
                resetTokenRepository,
                emailSender,
                passwordEncoder,
                rateLimiter,
                clock
        );

        ReflectionTestUtils.setField(passwordResetService, "authRateLimit", 10);
        ReflectionTestUtils.setField(passwordResetService, "resetTokenMinutes", 30L);
        ReflectionTestUtils.setField(passwordResetService, "resetLinkBase", "http://localhost:3000/reset-password");
    }

    @Test
    void unknownAndKnownEmails_getIdenticalResponse() {
        String knownEmail = "known@campus.edu";
        String unknownEmail = "unknown@campus.edu";

        User user = User.builder()
                .id(1L)
                .email(knownEmail)
                .name("Known User")
                .isActive(true)
                .build();

        when(userRepository.findByEmail(knownEmail)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail(unknownEmail)).thenReturn(Optional.empty());

        MessageResponse responseKnown = passwordResetService.forgotPassword(
                new ForgotPasswordRequest(knownEmail), "127.0.0.1"
        );
        MessageResponse responseUnknown = passwordResetService.forgotPassword(
                new ForgotPasswordRequest(unknownEmail), "127.0.0.1"
        );

        assertEquals(responseKnown.message(), responseUnknown.message());
        verify(emailSender, times(1)).sendPasswordResetEmail(eq(knownEmail), anyString());
        verify(emailSender, never()).sendPasswordResetEmail(eq(unknownEmail), anyString());
    }

    @Test
    void tokenWorksOnce() {
        String rawToken = "raw-test-token-12345678901234567890";
        String tokenHash = TokenGenerator.sha256Hex(rawToken);

        User user = User.builder()
                .id(1L)
                .email("user@campus.edu")
                .passwordHash("old_hash")
                .build();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(10L)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(now.plus(Duration.ofMinutes(30)))
                .usedAt(null)
                .build();

        when(resetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new_hash");

        MessageResponse response = passwordResetService.resetPassword(
                new ResetPasswordRequest(rawToken, "newPassword123")
        );
        assertNotNull(response);
        assertNotNull(resetToken.getUsedAt());
        assertEquals("new_hash", user.getPasswordHash());
        assertEquals(now, user.getPasswordChangedAt());

        // Attempting to use the same token again should fail
        ApiException exception = assertThrows(ApiException.class, () ->
                passwordResetService.resetPassword(new ResetPasswordRequest(rawToken, "anotherPassword123"))
        );
        assertEquals("INVALID_RESET_TOKEN", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void expiredToken_rejected() {
        String rawToken = "expired-token-12345678901234567890";
        String tokenHash = TokenGenerator.sha256Hex(rawToken);

        User user = User.builder()
                .id(1L)
                .email("user@campus.edu")
                .build();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(10L)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(now.minus(Duration.ofMinutes(5))) // Expired 5 mins ago
                .usedAt(null)
                .build();

        when(resetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        ApiException exception = assertThrows(ApiException.class, () ->
                passwordResetService.resetPassword(new ResetPasswordRequest(rawToken, "newPassword123"))
        );
        assertEquals("INVALID_RESET_TOKEN", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void onlyHashIsStored() {
        String email = "student@campus.edu";
        User user = User.builder()
                .id(2L)
                .email(email)
                .name("Student")
                .isActive(true)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        passwordResetService.forgotPassword(new ForgotPasswordRequest(email), "192.168.1.1");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(tokenCaptor.capture());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendPasswordResetEmail(eq(email), linkCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        String sentLink = linkCaptor.getValue();

        String rawToken = sentLink.substring(sentLink.indexOf("token=") + 6);

        // Verify that raw token is NOT stored in entity
        assertNotEquals(rawToken, savedToken.getTokenHash());
        // Verify only 64-char SHA-256 hex is stored
        assertEquals(64, savedToken.getTokenHash().length());
        assertEquals(TokenGenerator.sha256Hex(rawToken), savedToken.getTokenHash());
    }

    @Test
    void jwtIssuedBeforeReset_rejectedAfterwards() {
        JwtService jwtService = new JwtService("very-long-secret-key-that-is-at-least-32-bytes!", 60, clock);

        User user = User.builder()
                .id(1L)
                .email("student@campus.edu")
                .role(Role.STUDENT)
                .isActive(true)
                .build();

        // Token issued at 'now'
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        Claims claims = jwtService.parseAndValidate(token);
        Instant tokenIssuedAt = jwtService.extractIssuedAt(claims);

        // Advance user's passwordChangedAt by 1 minute
        Instant resetTime = now.plus(Duration.ofMinutes(1));
        user.setPasswordChangedAt(resetTime);

        // Token issued before passwordChangedAt must be detected as outdated
        assertTrue(tokenIssuedAt.isBefore(user.getPasswordChangedAt()));
    }
}
