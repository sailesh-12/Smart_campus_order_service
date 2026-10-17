package com.smartcampus.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long menuItemId,
        String itemName,
        int qty,
        BigDecimal unitPrice
) {
}
