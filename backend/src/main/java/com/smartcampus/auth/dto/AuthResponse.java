package com.smartcampus.auth.dto;

import com.smartcampus.user.dto.UserProfileResponse;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        UserProfileResponse user
) {
    public AuthResponse(String token, long expiresInSeconds, UserProfileResponse user) {
        this(token, "Bearer", expiresInSeconds, user);
    }
}
