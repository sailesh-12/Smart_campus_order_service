package com.smartcampus.order.dto;

public record VerifyPickupRequest(
        String pickupCode,
        String pickupSecret
) {
}
