package com.smartcampus.menu.dto;

import java.math.BigDecimal;

/**
 * Menu item representation for vendor owners, including exact stock quantities.
 */
public record MenuItemVendorResponse(
        Long id,
        Long vendorId,
        String name,
        BigDecimal price,
        int stockQty,
        boolean isAvailable,
        boolean lowStock,
        String description,
        String category,
        String imageUrl,
        int prepMinutes
) {
}
