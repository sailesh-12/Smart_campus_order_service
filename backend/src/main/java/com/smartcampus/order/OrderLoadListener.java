package com.smartcampus.order;

import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.vendor.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Section 6: when a shop's order set changes (new paid order, accepted, ready, collected, cancelled, rejected ...)
 * the cached busy level is dropped and open orders get their ready time recomputed (OrderEtaUpdated if it moved >= 2 min).
 * Runs after commit and asynchronously, so it never slows down or fails the change itself.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class OrderLoadListener {

    private final EstimateService estimateService;
    private final VendorService vendorService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChanged event) {
        if (event.status() == OrderStatus.PENDING_PAYMENT || event.status() == OrderStatus.EXPIRED) {
            return; // unpaid orders are not in the kitchen backlog
        }
        try {
            vendorService.onLoadChanged(event.vendorId());
            estimateService.onVendorOrderSetChanged(event.vendorId());
        } catch (RuntimeException e) {
            log.warn("ETA recompute for vendor {} failed: {}", event.vendorId(), e.getMessage());
        }
    }
}
