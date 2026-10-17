package com.smartcampus.vendor.dto;

import com.smartcampus.vendor.BusyLevel;
import com.smartcampus.vendor.VendorType;

public record VendorResponse(
        Long id,
        String name,
        VendorType type,
        boolean isOpen,
        boolean rushMode,
        int avgPrepMinutes,
        int slotMaxOrders,
        boolean isSuspended,
        int parallelCapacity,
        BusyLevel busyLevel,
        int estimatedWaitMinutes
) {
}
