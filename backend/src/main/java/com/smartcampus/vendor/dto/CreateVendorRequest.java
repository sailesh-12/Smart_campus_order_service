package com.smartcampus.vendor.dto;

import com.smartcampus.vendor.VendorType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVendorRequest(
        @NotBlank(message = "Vendor name is required")
        @Size(max = 100)
        String name,

        @NotNull(message = "Vendor type is required")
        VendorType type,

        @NotNull(message = "Owner user id is required")
        Long ownerUserId,

        @Min(value = 1, message = "Parallel capacity must be at least 1")
        Integer parallelCapacity,

        @Min(value = 1, message = "Slot max orders must be at least 1")
        Integer slotMaxOrders,

        @Min(value = 1, message = "Average prep minutes must be at least 1")
        Integer avgPrepMinutes
) {
}
