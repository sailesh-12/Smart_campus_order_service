package com.smartcampus.analytics;

import com.smartcampus.analytics.dto.AdminAnalyticsResponse;
import com.smartcampus.complaint.ComplaintService;
import com.smartcampus.order.OrderService;
import com.smartcampus.payment.PaymentService;
import com.smartcampus.user.Role;
import com.smartcampus.user.UserService;
import com.smartcampus.vendor.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campus-wide dashboard numbers. Revenue counts orders whose payment went through and that were not
 * cancelled, rejected or expired (manual refunds on such orders are not subtracted).
 */
@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final OrderService orderService;
    private final VendorService vendorService;
    private final UserService userService;
    private final ComplaintService complaintService;
    private final PaymentService paymentService;

    @Override
    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAdminAnalytics() {
        return new AdminAnalyticsResponse(
                orderService.totalRevenue(),
                orderService.totalOrders(),
                vendorService.countActiveVendors(),
                userService.countActiveByRole(Role.STUDENT),
                complaintService.countUnresolved(),
                paymentService.countUnresolvedIssues());
    }
}
