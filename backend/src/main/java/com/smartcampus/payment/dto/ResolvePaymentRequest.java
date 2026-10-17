package com.smartcampus.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolvePaymentRequest(
        @NotBlank(message = "Resolution note is required")
        @Size(max = 300)
        String note
) {
}
