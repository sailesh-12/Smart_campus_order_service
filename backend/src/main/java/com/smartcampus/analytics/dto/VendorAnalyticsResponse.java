package com.smartcampus.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record VendorAnalyticsResponse(
        long vendorId,
        BigDecimal totalRevenue,
        long totalOrders,
        long completedOrders,
        long cancelledOrders,
        List<TopItemResponse> topItems
) {
}
