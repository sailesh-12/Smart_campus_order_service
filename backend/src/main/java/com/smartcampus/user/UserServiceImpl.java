package com.smartcampus.user;

import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.user.dto.UpdateUserRequest;
import com.smartcampus.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        user = userRepository.save(user);
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(long userId) {
        return userRepository.findById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional
    public void updatePasswordChangedAt(long userId, Instant timestamp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        user.setPasswordChangedAt(timestamp);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void setPasswordHash(long userId, String passwordHash) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        user.setPasswordHash(passwordHash);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void setActiveStatus(long userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        user.setActive(active);
        userRepository.save(user);
    }
}
