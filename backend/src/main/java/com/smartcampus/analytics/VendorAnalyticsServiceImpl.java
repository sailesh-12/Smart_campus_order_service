package com.smartcampus.analytics;

import com.smartcampus.analytics.dto.VendorAnalyticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class VendorAnalyticsServiceImpl implements VendorAnalyticsService {

    @Override
    @Transactional(readOnly = true)
    public VendorAnalyticsResponse getVendorAnalytics(long vendorId, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("TODO");
    }
}
