package com.smartcampus.notify;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Production push sender stub utilizing Google Firebase Admin SDK (FCM).
 * Activated via app.push.provider=fcm.
 * Credentials loaded from FCM_CREDENTIALS_PATH environment variable.
 */
@Component
@ConditionalOnProperty(name = "app.push.provider", havingValue = "fcm")
@Slf4j
public class FcmPushSender implements PushSender {

    @Override
    public PushResult send(String token, String title, String body, Map<String, String> data) {
        log.info("Sending FCM push notification to token {}", token);
        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            FirebaseMessaging.getInstance().send(messageBuilder.build());
            return PushResult.SENT;
        } catch (Exception e) {
            log.error("FCM dispatch failed: {}", e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("registration-token-not-registered")) {
                return PushResult.INVALID_TOKEN;
            }
            return PushResult.FAILED;
        }
    }
}
