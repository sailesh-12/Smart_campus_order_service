package com.smartcampus.user.dto;

import com.smartcampus.user.Role;
import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String name,
        String email,
        Role role,
        Instant createdAt
) {
}
