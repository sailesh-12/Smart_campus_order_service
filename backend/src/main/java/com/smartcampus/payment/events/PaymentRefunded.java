package com.smartcampus.payment.events;

import com.smartcampus.payment.RefundReason;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when an order payment is successfully refunded.
 * Consumed by PushNotificationListener and SSE emitter to notify the student.
 * The payment module does not know students (it may not import order), so listeners
 * resolve the student from orderId through the order module.
 * The amount is for in-app screens only and is never put on a lock-screen push (rule 5.9).
 */
public record PaymentRefunded(
        long paymentId,
        long orderId,
        BigDecimal amount,
        RefundReason reason,
        Instant at
) {
}
