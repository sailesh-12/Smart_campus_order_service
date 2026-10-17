package com.smartcampus.menu.dto;

import java.math.BigDecimal;

/**
 * Menu item representation for student clients.
 * Never exposes exact stock quantity, only available and lowStock flags.
 */
public record MenuItemStudentResponse(
        Long id,
        Long vendorId,
        String name,
        BigDecimal price,
        boolean available,
        boolean lowStock,
        String description,
        String category,
        String imageUrl,
        int prepMinutes,
        EtaRange estimatedTime
) {
}
