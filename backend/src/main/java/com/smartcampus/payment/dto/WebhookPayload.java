package com.smartcampus.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebhookPayload(
        @NotBlank(message = "Event ID is required")
        String eventId,

        @NotNull(message = "Payment ID is required")
        Long paymentId,

        @NotBlank(message = "Status is required")
        String status,

        String providerRef
) {
}
