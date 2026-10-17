package com.smartcampus.order.dto;

import java.time.LocalDate;

public record PickupCredentialsResponse(
        Long orderId,
        String pickupCode,
        String pickupSecret,
        LocalDate pickupDate
) {
}
