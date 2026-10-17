package com.smartcampus.order.dto;

import java.time.Instant;

public record SlotResponse(
        Instant slotStart,
        int maxOrders,
        int bookedOrders,
        boolean available
) {
}
