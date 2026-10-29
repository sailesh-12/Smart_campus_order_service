package com.smartcampus.order.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order figures for one shop and period, used by the analytics module.
 * Revenue counts orders whose payment went through and that were not cancelled/rejected/expired.
 */
public record VendorOrderStats(
        BigDecimal revenue,
        long totalOrders,
        long completedOrders,
        long cancelledOrders,
        List<TopItem> topItems
) {
    public record TopItem(Long menuItemId, String itemName, long totalSold, BigDecimal totalRevenue) {
    }
}
