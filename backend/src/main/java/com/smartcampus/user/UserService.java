package com.smartcampus.user;

import com.smartcampus.user.dto.UpdateUserRequest;
import com.smartcampus.user.dto.UserProfileResponse;

import java.time.Instant;
import java.util.Optional;

/**
 * Service managing user profiles, status, and authentication credentials.
 */
public interface UserService {

    UserProfileResponse getProfile(long userId);

    UserProfileResponse updateProfile(long userId, UpdateUserRequest request);

    Optional<User> findById(long userId);

    Optional<User> findByEmail(String email);

    void updatePasswordChangedAt(long userId, Instant timestamp);

    void setPasswordHash(long userId, String passwordHash);

    void setActiveStatus(long userId, boolean active);
}
