package com.smartcampus.analytics;

import com.smartcampus.analytics.dto.AdminAnalyticsResponse;
import com.smartcampus.analytics.dto.VendorAnalyticsResponse;
import com.smartcampus.audit.AdminAction;
import com.smartcampus.audit.AuditService;
import com.smartcampus.common.security.CurrentUser;
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

    private final VendorAnalyticsService vendorAnalyticsService;
    private final AdminAnalyticsService adminAnalyticsService;
    private final AuditService auditService;
    private final VendorService vendorService;
    private final CurrentUser currentUser;

    @GetMapping("/vendor/analytics")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorAnalyticsResponse> getVendorAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/admin/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminAnalyticsResponse> getAdminAnalytics() {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/admin/actions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminAction>> getAdminAuditLogs() {
        return ResponseEntity.ok(auditService.getActions());
    }

    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> getAdminOrders() {
        throw new UnsupportedOperationException("TODO");
    }
}
