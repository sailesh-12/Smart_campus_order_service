package com.smartcampus.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.auth.dto.AuthResponse;
import com.smartcampus.auth.dto.ForgotPasswordRequest;
import com.smartcampus.auth.dto.LoginRequest;
import com.smartcampus.auth.dto.MessageResponse;
import com.smartcampus.auth.dto.RefreshTokenRequest;
import com.smartcampus.auth.dto.RegisterRequest;
import com.smartcampus.auth.dto.ResetPasswordRequest;
import com.smartcampus.user.Role;
import com.smartcampus.user.dto.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private PasswordResetService passwordResetService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        passwordResetService = mock(PasswordResetService.class);
        AuthController controller = new AuthController(authService, passwordResetService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void register_returnsCreated() throws Exception {
        RegisterRequest request = new RegisterRequest("Student Test", "student@test.edu", "Password123", Role.STUDENT);
        AuthResponse response = new AuthResponse("mock-jwt-token", 3600L,
                new UserProfileResponse(1L, "Student Test", "student@test.edu", Role.STUDENT, Instant.now()));

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("student@test.edu"));

        verify(authService).register(eq(request));
    }

    @Test
    void login_returnsOk() throws Exception {
        LoginRequest request = new LoginRequest("student@test.edu", "Password123");
        AuthResponse response = new AuthResponse("mock-jwt-token", 3600L,
                new UserProfileResponse(1L, "Student Test", "student@test.edu", Role.STUDENT, Instant.now()));

        when(authService.login(any(LoginRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", "203.0.113.195, 70.41.3.18")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"));

        verify(authService).login(eq(request), eq("203.0.113.195"));
    }

    @Test
    void refresh_returnsOk() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("existing-jwt");
        AuthResponse response = new AuthResponse("new-jwt-token", 3600L,
                new UserProfileResponse(1L, "Student Test", "student@test.edu", Role.STUDENT, Instant.now()));

        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-jwt-token"));

        verify(authService).refresh(eq(request));
    }

    @Test
    void forgotPassword_returnsOk() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("student@test.edu");
        MessageResponse response = new MessageResponse("If the email is registered, a password reset link has been sent.");

        when(passwordResetService.forgotPassword(any(ForgotPasswordRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(response.message()));

        verify(passwordResetService).forgotPassword(eq(request), any());
    }

    @Test
    void resetPassword_returnsOk() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("token-123", "newPassword123");
        MessageResponse response = new MessageResponse("Password has been reset successfully");

        when(passwordResetService.resetPassword(any(ResetPasswordRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(response.message()));

        verify(passwordResetService).resetPassword(eq(request));
    }
}
