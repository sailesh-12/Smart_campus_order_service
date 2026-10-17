package com.smartcampus.notify;

import com.smartcampus.notify.dto.RegisterDeviceRequest;

import java.util.List;

/**
 * Service managing registered FCM mobile device tokens.
 */
public interface DeviceTokenService {

    /**
     * Business Rule (5.9):
     * - Saves phone token for authenticated user.
     * - If token already exists under another user, moves it to this user.
     * - Keeps at most app.push.max-tokens-per-user tokens per user, deleting oldest.
     */
    void registerToken(long userId, RegisterDeviceRequest request);

    /**
     * Business Rule (5.9):
     * Deletes token on user logout.
     */
    void deleteToken(String token, long userId);

    List<DeviceToken> getTokensForUser(long userId);

    void removeInvalidToken(String token);
}
