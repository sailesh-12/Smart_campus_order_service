package com.smartcampus.analytics.dto;

import java.math.BigDecimal;

public record AdminAnalyticsResponse(
        BigDecimal totalCampusRevenue,
        long totalOrders,
        long activeVendors,
        long activeStudents,
        long unresolvedComplaints,
        long unresolvedPaymentIssues
) {
}
