package com.smartcampus.analytics;

import com.smartcampus.analytics.dto.AdminAnalyticsResponse;
import com.smartcampus.analytics.dto.VendorAnalyticsResponse;
import com.smartcampus.audit.AdminAction;
import com.smartcampus.audit.AuditService;
import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.order.OrderService;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.vendor.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    static final int ADMIN_ORDERS_MAX = 500;

    private final VendorAnalyticsService vendorAnalyticsService;
    private final AdminAnalyticsService adminAnalyticsService;
    private final AuditService auditService;
    private final VendorService vendorService;
    private final OrderService orderService;
    private final CurrentUser currentUser;

    @GetMapping("/vendor/analytics")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorAnalyticsResponse> getVendorAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        long vendorId = vendorService.vendorIdOf(currentUser.id());
        return ResponseEntity.ok(vendorAnalyticsService.getVendorAnalytics(vendorId, from, to));
    }

    @GetMapping("/admin/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminAnalyticsResponse> getAdminAnalytics() {
        return ResponseEntity.ok(adminAnalyticsService.getAdminAnalytics());
    }

    @GetMapping("/admin/actions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminAction>> getAdminAuditLogs() {
        return ResponseEntity.ok(auditService.getActions());
    }

    /** Most recent orders across campus (limit 1..500, default 100). */
    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> getAdminOrders(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, ADMIN_ORDERS_MAX));
        return ResponseEntity.ok(orderService.getRecentOrders(safeLimit));
    }
}
