package com.smartcampus.analytics;

import com.smartcampus.analytics.dto.TopItemResponse;
import com.smartcampus.analytics.dto.VendorAnalyticsResponse;
import com.smartcampus.common.exception.ApiException;
import com.smartcampus.order.OrderService;
import com.smartcampus.order.dto.VendorOrderStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Shop figures for a date range (campus time zone, both ends inclusive).
 * Without from/to the last 30 days including today are used.
 */
@Service
@RequiredArgsConstructor
public class VendorAnalyticsServiceImpl implements VendorAnalyticsService {

    static final int DEFAULT_DAYS = 30;
    static final int TOP_ITEMS = 5;

    private final OrderService orderService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public VendorAnalyticsResponse getVendorAnalytics(long vendorId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "from", "from must not be after to");
        }
        Instant fromInstant = start.atStartOfDay(clock.getZone()).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(clock.getZone()).toInstant();

        VendorOrderStats stats = orderService.getVendorStats(vendorId, fromInstant, toInstant, TOP_ITEMS);
        return new VendorAnalyticsResponse(
                vendorId,
                stats.revenue(),
                stats.totalOrders(),
                stats.completedOrders(),
                stats.cancelledOrders(),
                stats.topItems().stream()
                        .map(t -> new TopItemResponse(t.menuItemId(), t.itemName(), t.totalSold(), t.totalRevenue()))
                        .toList());
    }
}
