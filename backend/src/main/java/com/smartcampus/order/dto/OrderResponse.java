package com.smartcampus.order.dto;

import com.smartcampus.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long studentId,
        Long vendorId,
        OrderStatus status,
        BigDecimal total,
        Instant createdAt,
        Instant holdExpiresAt,
        Integer estMinutesLow,
        Integer estMinutesHigh,
        Instant estReadyAt,
        Instant pickupSlotStart,
        Long paymentId,
        List<OrderItemResponse> items
) {
}
