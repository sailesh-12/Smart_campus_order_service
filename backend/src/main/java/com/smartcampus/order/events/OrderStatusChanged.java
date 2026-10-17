package com.smartcampus.order.events;

import com.smartcampus.order.OrderStatus;

import java.time.Instant;

/**
 * Published by OrderService inside the transaction that changes an order's status.
 * Carries only what the clients need to update their screens (no entities, no secrets).
 */
public record OrderStatusChanged(
        long orderId,
        long vendorId,
        OrderStatus status,
        Instant at) {
}
