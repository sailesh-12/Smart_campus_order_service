package com.smartcampus.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default development push sender that logs messages instead of contacting remote FCM servers.
 */
@Component
@ConditionalOnProperty(name = "app.push.provider", havingValue = "log", matchIfMissing = true)
@Slf4j
public class LoggingPushSender implements PushSender {

    @Override
    public PushResult send(String token, String title, String body, Map<String, String> data) {
        log.info("[MOCK PUSH] Dispatched to token={} | Title='{}' | Body='{}' | Data={}", token, title, body, data);
        return PushResult.SENT;
    }
}
