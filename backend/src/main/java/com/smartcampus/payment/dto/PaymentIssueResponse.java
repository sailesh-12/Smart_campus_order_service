package com.smartcampus.payment.dto;

import com.smartcampus.payment.PaymentStatus;
import com.smartcampus.payment.RefundReason;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representation of an unresolved payment issue for the admin dashboard (rule 5.12).
 */
public record PaymentIssueResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt,
        long ageMinutes,
        int checkCount,
        int refundAttempts,
        RefundReason refundReason,
        String failureReason
) {
}
