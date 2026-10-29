package com.smartcampus.order.dto;

/**
 * Result of POST /orders: the order, and whether it was an idempotent replay
 * (200 + header Idempotent-Replayed: true) instead of a new order (201).
 */
public record PlaceOrderResult(OrderResponse order, boolean replayed) {
}
