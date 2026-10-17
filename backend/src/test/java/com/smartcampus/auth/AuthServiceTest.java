package com.smartcampus.auth;

import com.smartcampus.auth.dto.AuthResponse;
import com.smartcampus.auth.dto.LoginRequest;
import com.smartcampus.auth.dto.RefreshTokenRequest;
import com.smartcampus.auth.dto.RegisterRequest;
import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.exception.ForbiddenException;
import com.smartcampus.common.security.JwtService;
import com.smartcampus.common.util.RateLimiter;
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
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RateLimiter rateLimiter;
    private Clock clock;
    private AuthServiceImpl authService;

    private final Instant now = Instant.parse("2026-10-17T12:00:00Z");

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        jwtService = new JwtService("very-long-secret-key-that-is-at-least-32-bytes!", 60, clock);
        rateLimiter = mock(RateLimiter.class);

        authService = new AuthServiceImpl(
                userRepository,
                passwordEncoder,
                jwtService,
                rateLimiter
        );

        ReflectionTestUtils.setField(authService, "authRateLimit", 10);
        ReflectionTestUtils.setField(authService, "jwtExpirationMinutes", 60L);
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("New Student", "student@campus.edu", "Password123", Role.STUDENT);

        when(userRepository.existsByEmail("student@campus.edu")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded_pass");

        User savedUser = User.builder()
                .id(100L)
                .name("New Student")
                .email("student@campus.edu")
                .passwordHash("encoded_pass")
                .role(Role.STUDENT)
                .isActive(true)
                .createdAt(now)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresInSeconds());
        assertEquals(100L, response.user().id());
        assertEquals("student@campus.edu", response.user().email());
        assertEquals(Role.STUDENT, response.user().role());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User captured = captor.getValue();
        assertEquals("student@campus.edu", captured.getEmail());
        assertEquals("encoded_pass", captured.getPasswordHash());
        assertTrue(captured.isActive());
    }

    @Test
    void register_duplicateEmail_throwsConflictException() {
        RegisterRequest request = new RegisterRequest("Existing", "existing@campus.edu", "Password123", Role.STUDENT);
        when(userRepository.existsByEmail("existing@campus.edu")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> authService.register(request));
        assertEquals("EMAIL_ALREADY_EXISTS", ex.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("student@campus.edu", "Password123");
        User user = User.builder()
                .id(100L)
                .name("Student")
                .email("student@campus.edu")
                .passwordHash("encoded_pass")
                .role(Role.STUDENT)
                .isActive(true)
                .createdAt(now)
                .build();

        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "encoded_pass")).thenReturn(true);

        AuthResponse response = authService.login(request, "127.0.0.1");

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals(100L, response.user().id());
        verify(rateLimiter).checkLimit(eq("rate:auth:ip:"), eq("127.0.0.1"), eq(10), eq(false));
        verify(rateLimiter).checkLimit(eq("rate:auth:email:"), eq("student@campus.edu"), eq(10), eq(false));
    }

    @Test
    void login_invalidPassword_throwsApiException() {
        LoginRequest request = new LoginRequest("student@campus.edu", "WrongPassword");
        User user = User.builder()
                .id(100L)
                .name("Student")
                .email("student@campus.edu")
                .passwordHash("encoded_pass")
                .role(Role.STUDENT)
                .isActive(true)
                .build();

        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "encoded_pass")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request, "127.0.0.1"));
        assertEquals("INVALID_CREDENTIALS", ex.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void login_deactivatedUser_throwsForbiddenException() {
        LoginRequest request = new LoginRequest("inactive@campus.edu", "Password123");
        User user = User.builder()
                .id(100L)
                .name("Inactive Student")
                .email("inactive@campus.edu")
                .passwordHash("encoded_pass")
                .role(Role.STUDENT)
                .isActive(false)
                .build();

        when(userRepository.findByEmail("inactive@campus.edu")).thenReturn(Optional.of(user));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> authService.login(request, "127.0.0.1"));
        assertEquals("USER_INACTIVE", ex.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void refresh_success() {
        User user = User.builder()
                .id(100L)
                .name("Student")
                .email("student@campus.edu")
                .role(Role.STUDENT)
                .isActive(true)
                .createdAt(now)
                .build();

        String existingToken = jwtService.generateToken(100L, "student@campus.edu", "STUDENT");
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        AuthResponse response = authService.refresh(new RefreshTokenRequest(existingToken));

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals(100L, response.user().id());
    }
}
