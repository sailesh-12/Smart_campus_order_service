package com.smartcampus.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "Vendor id is required")
        Long vendorId,

        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<OrderItemRequest> items,

        Instant pickupSlotStart
) {
}
