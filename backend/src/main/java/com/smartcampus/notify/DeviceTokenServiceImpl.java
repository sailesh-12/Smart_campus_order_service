package com.smartcampus.notify;

import com.smartcampus.notify.dto.RegisterDeviceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final Clock clock;

    @Value("${app.push.max-tokens-per-user:5}")
    private int maxTokensPerUser;

    @Override
    @Transactional
    public void registerToken(long userId, RegisterDeviceRequest request) {
        Instant now = clock.instant();
        String token = request.token().trim();
        DeviceToken deviceToken = deviceTokenRepository.findByToken(token).orElse(null);
        if (deviceToken == null) {
            deviceToken = DeviceToken.builder()
                    .userId(userId)
                    .token(token)
                    .platform(request.platform())
                    .createdAt(now)
                    .lastSeenAt(now)
                    .build();
        } else {
            if (!deviceToken.getUserId().equals(userId)) {
                // Same phone, different account: the token now belongs to whoever logged in last
                log.info("Moving device token from user {} to user {}", deviceToken.getUserId(), userId);
                deviceToken.setUserId(userId);
            }
            deviceToken.setPlatform(request.platform());
            deviceToken.setLastSeenAt(now);
        }
        deviceTokenRepository.saveAndFlush(deviceToken);

        // Keep at most N tokens per user, dropping the oldest
        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdOrderByCreatedAtAsc(userId);
        int excess = tokens.size() - maxTokensPerUser;
        for (int i = 0; i < excess; i++) {
            deviceTokenRepository.delete(tokens.get(i));
        }
    }

    @Override
    @Transactional
    public void deleteToken(String token, long userId) {
        deviceTokenRepository.findByToken(token)
                .filter(t -> t.getUserId().equals(userId))
                .ifPresent(deviceTokenRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceToken> getTokensForUser(long userId) {
        return deviceTokenRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    @Override
    @Transactional
    public void removeInvalidToken(String token) {
        deviceTokenRepository.deleteByToken(token);
    }
}
