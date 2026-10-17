package com.smartcampus.payment.dto;

import com.smartcampus.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        String providerRef,
        Instant createdAt
) {
}
