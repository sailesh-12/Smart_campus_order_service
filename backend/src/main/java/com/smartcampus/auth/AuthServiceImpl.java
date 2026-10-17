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
import com.smartcampus.user.User;
import com.smartcampus.user.UserRepository;
import com.smartcampus.user.dto.UserProfileResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;

    @Value("${app.auth.rate-limit-per-minute:10}")
    private int authRateLimit;

    @Value("${app.jwt.expiration-minutes:60}")
    private long jwtExpirationMinutes;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail) || userRepository.existsByEmail(request.email().trim())) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Email is already registered: " + request.email());
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        UserProfileResponse profile = new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
        return new AuthResponse(token, jwtExpirationMinutes * 60, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String clientIp) {
        // Enforce rate limit (fail closed on redis error)
        String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp : "unknown";
        rateLimiter.checkLimit("rate:auth:ip:", ip, authRateLimit, false);
        rateLimiter.checkLimit("rate:auth:email:", request.email().trim().toLowerCase(), authRateLimit, false);

        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .or(() -> userRepository.findByEmail(request.email().trim()))
                .orElseThrow(() -> new ApiException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!user.isActive()) {
            throw new ForbiddenException("USER_INACTIVE", "Account is deactivated");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        UserProfileResponse profile = new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
        return new AuthResponse(token, jwtExpirationMinutes * 60, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        try {
            Claims claims = jwtService.parseAndValidate(request.refreshToken());
            Long userId = jwtService.extractUserId(claims);
            if (userId == null) {
                throw new ApiException("INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "Invalid token");
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "User not found"));
            if (!user.isActive()) {
                throw new ForbiddenException("USER_INACTIVE", "Account is deactivated");
            }
            Instant issuedAt = jwtService.extractIssuedAt(claims);
            if (user.getPasswordChangedAt() != null && issuedAt != null && issuedAt.isBefore(user.getPasswordChangedAt())) {
                throw new ApiException("TOKEN_REVOKED", HttpStatus.UNAUTHORIZED, "Token has been revoked");
            }
            String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
            UserProfileResponse profile = new UserProfileResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole(),
                    user.getCreatedAt()
            );
            return new AuthResponse(token, jwtExpirationMinutes * 60, profile);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }
}
