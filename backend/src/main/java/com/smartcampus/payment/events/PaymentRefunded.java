package com.smartcampus.payment.events;

import com.smartcampus.payment.RefundReason;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when an order payment is successfully refunded.
 * Consumed by PushNotificationListener and SSE emitter to notify the student.
 */
public record PaymentRefunded(
        long paymentId,
        long orderId,
        long studentId,
        BigDecimal amount,
        RefundReason reason,
        Instant at
) {
}
