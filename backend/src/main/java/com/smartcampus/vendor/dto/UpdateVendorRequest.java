package com.smartcampus.vendor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateVendorRequest(
        @Size(max = 100)
        String name,

        Boolean isOpen,

        Boolean rushMode,

        @Min(1)
        Integer avgPrepMinutes,

        @Min(1)
        Integer slotMaxOrders,

        Boolean isSuspended,

        @Min(1)
        Integer parallelCapacity
) {
}
