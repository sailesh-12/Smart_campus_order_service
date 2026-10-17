package com.smartcampus.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateMenuItemRequest(
        @NotBlank(message = "Item name is required")
        @Size(max = 100)
        String name,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
        BigDecimal price,

        @Min(value = 0, message = "Stock quantity cannot be negative")
        Integer stockQty,

        Boolean isAvailable,

        @Size(max = 255)
        String description,

        @Size(max = 50)
        String category,

        @Size(max = 255)
        String imageUrl,

        @Min(value = 1, message = "Prep minutes must be at least 1")
        Integer prepMinutes
) {
}
