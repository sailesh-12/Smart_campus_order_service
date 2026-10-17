package com.smartcampus.analytics;

import com.smartcampus.analytics.dto.AdminAnalyticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    @Override
    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAdminAnalytics() {
        throw new UnsupportedOperationException("TODO");
    }
}
