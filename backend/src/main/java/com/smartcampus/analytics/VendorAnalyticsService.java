package com.smartcampus.analytics;

import com.smartcampus.analytics.dto.VendorAnalyticsResponse;

import java.time.LocalDate;

public interface VendorAnalyticsService {

    VendorAnalyticsResponse getVendorAnalytics(long vendorId, LocalDate from, LocalDate to);
}
