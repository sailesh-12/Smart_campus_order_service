package com.smartcampus.analytics.dto;

import java.math.BigDecimal;

public record TopItemResponse(
        Long menuItemId,
        String itemName,
        long totalSold,
        BigDecimal totalRevenue
) {
}
