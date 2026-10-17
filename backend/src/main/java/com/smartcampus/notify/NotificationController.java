package com.smartcampus.notify;

import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.order.OrderService;
import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.vendor.VendorService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitterRegistry registry;
    private final OrderService orderService;   // assumed: currentStatusEvent(orderId, userId)
    private final VendorService vendorService; // assumed: vendorIdOf(userId)
    private final CurrentUser currentUser;

    /** Student: live status of one of their own orders. */
    @GetMapping(value = "/orders/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public SseEmitter orderEvents(@PathVariable long id, HttpServletResponse response) {
        // Throws 404/403 BEFORE the stream opens if the order isn't this student's
        OrderStatusChanged current = orderService.currentStatusEvent(id, currentUser.id());
        disableBuffering(response);
        SseEmitter emitter = registry.subscribeToOrder(id);
        // Send the current status immediately, so the screen is correct even if the
        // status changed between the page loading and the stream opening
        registry.sendTo(emitter, "order-status", current);
        return emitter;
    }

    /** Vendor: every order event for their own shop (new order, cancelled, and so on). */
    @GetMapping(value = "/vendor/orders/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('VENDOR')")
    public SseEmitter vendorStream(HttpServletResponse response) {
        long vendorId = vendorService.vendorIdOf(currentUser.id());
        disableBuffering(response);
        return registry.subscribeToVendor(vendorId);
    }

    /** Browsing/Menu: live stock and availability updates for a vendor's menu. */
    @GetMapping(value = "/vendors/{id}/menu/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter menuStream(@PathVariable long id, HttpServletResponse response) {
        disableBuffering(response);
        return registry.subscribeToMenu(id);
    }

    private void disableBuffering(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no"); // stops nginx-style proxies buffering the stream
    }
}
