package com.smartcampus.order.events;

import java.time.Instant;

/**
 * Rule 5.11: published once when a READY order has waited app.order.uncollected-reminder-minutes.
 * Delivered as an "order-reminder" SSE event and a push notification (no pickup code in the push).
 */
public record OrderReminder(
        long orderId,
        long vendorId,
        Instant at) {
}
