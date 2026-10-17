package com.smartcampus.menu.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAvailabilityRequest(
        @NotNull(message = "Availability flag is required")
        Boolean isAvailable
) {
}
