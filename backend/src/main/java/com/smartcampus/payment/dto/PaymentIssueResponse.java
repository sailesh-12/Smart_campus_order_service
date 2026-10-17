package com.smartcampus.payment.dto;

import com.smartcampus.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representation of an unresolved payment issue for the admin dashboard.
 */
public record PaymentIssueResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt,
        int checkCount,
        int refundAttempts,
        String failureReason
) {
}
