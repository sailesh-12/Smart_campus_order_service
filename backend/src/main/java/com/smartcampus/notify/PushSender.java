package com.smartcampus.notify;

import java.util.Map;

/**
 * Interface for dispatching mobile push notifications.
 */
public interface PushSender {

    /**
     * Dispatches a push notification to a device token.
     *
     * @param token device registration token
     * @param title notification title
     * @param body short message content (never contains pickup code or amount)
     * @param data key-value data payload (e.g. orderId, notification type)
     * @return PushResult (SENT, INVALID_TOKEN, or FAILED)
     */
    PushResult send(String token, String title, String body, Map<String, String> data);
}
