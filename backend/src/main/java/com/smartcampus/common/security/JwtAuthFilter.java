package com.smartcampus.common.security;

import com.smartcampus.user.User;
import com.smartcampus.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Filter that parses JWT Bearer tokens from the Authorization header.
 * Enforces session revocation: rejects any token issued before user's password_changed_at.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtService.parseAndValidate(token);
            Long userId = jwtService.extractUserId(claims);
            Instant issuedAt = jwtService.extractIssuedAt(claims);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (!user.isActive()) {
                        log.debug("User is inactive: {}", userId);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // Section 5.13: Rejects any token issued before password_changed_at
                    if (user.getPasswordChangedAt() != null && issuedAt != null) {
                        // Truncate to seconds / check if token issued prior to password change
                        if (issuedAt.isBefore(user.getPasswordChangedAt())) {
                            log.debug("Token issued before password_changed_at for user: {}", userId);
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }

                    CurrentUser currentUser = new CurrentUser(user.getId(), user.getEmail(), user.getRole().name());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            currentUser,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
