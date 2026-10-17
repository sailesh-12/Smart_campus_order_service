package com.smartcampus.order.events;

import java.time.Instant;

/**
 * Event published when an active order's estimated ready time is recomputed and moves by 2+ minutes.
 * Broadcast over SSE to the student tracking the order.
 */
public record OrderEtaUpdated(
        long orderId,
        long vendorId,
        int estMinutesLow,
        int estMinutesHigh,
        Instant estReadyAt,
        Instant at
) {
}
