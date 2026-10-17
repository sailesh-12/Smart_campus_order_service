package com.smartcampus.auth;

import com.smartcampus.auth.dto.AuthResponse;
import com.smartcampus.auth.dto.LoginRequest;
import com.smartcampus.auth.dto.RefreshTokenRequest;
import com.smartcampus.auth.dto.RegisterRequest;

/**
 * Authentication service handling user registration, login, and token refreshment.
 */
public interface AuthService {

    /**
     * Registers a new user.
     * Validates that the email is unique, hashes password using BCrypt,
     * stores user in database and returns initial JWT.
     *
     * @param request registration details
     * @return AuthResponse with JWT and profile
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates a user with email and password.
     * Business Rule (5.10):
     * Login attempts are rate limited at app.auth.rate-limit-per-minute per IP and email.
     * On rate limit exceed returns 429 with Retry-After header.
     * If Redis is unavailable, must fail closed for login attempts (blocking access) for security.
     *
     * @param request login credentials
     * @param clientIp caller IP address for rate limiting
     * @return AuthResponse with JWT and profile
     */
    AuthResponse login(LoginRequest request, String clientIp);

    /**
     * Refreshes an active session token.
     *
     * @param request token refresh request
     * @return AuthResponse with new JWT
     */
    AuthResponse refresh(RefreshTokenRequest request);
}
