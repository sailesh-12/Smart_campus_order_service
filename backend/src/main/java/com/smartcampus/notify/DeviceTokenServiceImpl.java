package com.smartcampus.notify;

import com.smartcampus.notify.dto.RegisterDeviceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Value("${app.push.max-tokens-per-user:5}")
    private int maxTokensPerUser;

    @Override
    @Transactional
    public void registerToken(long userId, RegisterDeviceRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public void deleteToken(String token, long userId) {
        throw new UnsupportedOperationException("TODO");
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
