package com.smartcampus.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateComplaintRequest(
        @NotNull(message = "Order id is required")
        Long orderId,

        @NotBlank(message = "Complaint text cannot be blank")
        @Size(max = 1000, message = "Complaint text must not exceed 1000 characters")
        String text
) {
}
