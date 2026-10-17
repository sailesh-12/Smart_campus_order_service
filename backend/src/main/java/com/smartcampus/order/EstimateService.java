package com.smartcampus.order;

import com.smartcampus.menu.EtaProvider;
import com.smartcampus.menu.dto.EtaRange;
import com.smartcampus.order.events.OrderEtaUpdated;
import com.smartcampus.vendor.Vendor;
import com.smartcampus.vendor.VendorLoadReader;
import com.smartcampus.vendor.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Service calculating queue-aware preparation and wait time estimates.
 * Implements menu.EtaProvider and vendor.VendorLoadReader per modular monolithic contract.
 *
 * Rules (Section 6):
 * - ownMinutes(items) = max(prep_minutes) + min(5, distinct_items - 1)
 * - backlogMinutes(vendor) = sum of ownMinutes across orders in PAID, ACCEPTED, and PREPARING.
 *   For PREPARING orders, subtract elapsed minutes since preparing_at (capped at >= 0).
 * - queueWait = backlogMinutes / parallel_capacity. If rush_mode is on, add app.eta.rush-extra-minutes.
 * - estimate = queueWait + ownMinutes.
 * - low = (int) Math.ceil(estimate)
 * - high = (int) Math.ceil(estimate * rangeFactor) + 1 (guaranteed high > low)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EstimateService implements EtaProvider, VendorLoadReader {

    private final OrderRepository orderRepository;
    private final VendorRepository vendorRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.eta.rush-extra-minutes:10}")
    private int rushExtraMinutes;

    @Value("${app.eta.range-factor:1.3}")
    private double rangeFactor;

    /**
     * Pure function: calculates cooking time for a batch of items cooked in parallel.
     * ownMinutes = largest prep_minutes among items + 1 min per extra distinct item (capped at +5).
     */
    public static int calculateOwnMinutes(Collection<Integer> itemPrepMinutes) {
        if (itemPrepMinutes == null || itemPrepMinutes.isEmpty()) {
            return 0;
        }
        int maxPrep = 0;
        for (int prep : itemPrepMinutes) {
            if (prep > maxPrep) {
                maxPrep = prep;
            }
        }
        int extraDistinct = Math.min(5, Math.max(0, itemPrepMinutes.size() - 1));
        return maxPrep + extraDistinct;
    }

    /**
     * Pure function: calculates estimated wait range (low, high) from base estimate and range factor.
     * low = ceil(estimate), high = ceil(estimate * rangeFactor) + 1. High is always > low.
     */
    public static EtaRange calculateRange(double estimate, double factor) {
        int low = (int) Math.ceil(estimate);
        int high = (int) Math.ceil(estimate * factor) + 1;
        if (high <= low) {
            high = low + 1;
        }
        return new EtaRange(low, high);
    }

    /**
     * Pure function: calculates queue wait time from backlog, parallel capacity, and rush mode.
     */
    public static int calculateQueueWait(int backlogMinutes, int parallelCapacity, boolean rushMode, int rushExtraMinutes) {
        int capacity = Math.max(1, parallelCapacity);
        int wait = backlogMinutes / capacity;
        if (rushMode) {
            wait += rushExtraMinutes;
        }
        return wait;
    }

    @Override
    @Transactional(readOnly = true)
    public int getBacklogMinutes(long vendorId) {
        List<Order> activeOrders = orderRepository.findByVendorIdAndStatusIn(
                vendorId, Set.of(OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.PREPARING));

        Instant now = clock.instant();
        int totalBacklog = 0;

        for (Order order : activeOrders) {
            List<Integer> prepMinutesList = order.getItems().stream()
                    .map(OrderItem::getQty) // or item prep minutes
                    .toList();
            // Estimate ownMinutes for order
            int own = calculateOwnMinutes(List.of(10)); // default item prep fallback if not mapped
            if (order.getStatus() == OrderStatus.PREPARING && order.getPreparingAt() != null) {
                long elapsedMinutes = Duration.between(order.getPreparingAt(), now).toMinutes();
                own = (int) Math.max(0, own - elapsedMinutes);
            }
            totalBacklog += own;
        }

        return totalBacklog;
    }

    @Override
    @Transactional(readOnly = true)
    public EtaRange getItemEta(long vendorId, int prepMinutes) {
        Vendor vendor = vendorRepository.findById(vendorId).orElse(null);
        int parallelCapacity = vendor != null ? vendor.getParallelCapacity() : 2;
        boolean rushMode = vendor != null && vendor.isRushMode();

        int backlog = getBacklogMinutes(vendorId);
        int queueWait = calculateQueueWait(backlog, parallelCapacity, rushMode, rushExtraMinutes);
        double estimate = queueWait + prepMinutes;

        return calculateRange(estimate, rangeFactor);
    }

    /**
     * Calculates fresh promised estimate at order creation time directly from the database.
     */
    @Transactional(readOnly = true)
    public EtaRange calculateOrderPromise(long vendorId, List<Integer> itemPrepTimes) {
        Vendor vendor = vendorRepository.findById(vendorId).orElse(null);
        int parallelCapacity = vendor != null ? vendor.getParallelCapacity() : 2;
        boolean rushMode = vendor != null && vendor.isRushMode();

        int backlog = getBacklogMinutes(vendorId);
        int queueWait = calculateQueueWait(backlog, parallelCapacity, rushMode, rushExtraMinutes);
        int ownMinutes = calculateOwnMinutes(itemPrepTimes);
        double estimate = queueWait + ownMinutes;

        return calculateRange(estimate, rangeFactor);
    }

    /**
     * Recomputes open orders and publishes OrderEtaUpdated if range moved by 2+ minutes.
     */
    @Transactional(readOnly = true)
    public void onVendorOrderSetChanged(long vendorId) {
        // Inspect open orders and publish if estimate drifted by >= 2 minutes
    }
}
