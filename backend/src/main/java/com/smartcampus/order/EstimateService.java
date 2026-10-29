package com.smartcampus.order;

import com.smartcampus.menu.EtaProvider;
import com.smartcampus.menu.dto.EtaRange;
import com.smartcampus.order.events.OrderEtaUpdated;
import com.smartcampus.vendor.Vendor;
import com.smartcampus.vendor.VendorBacklogCache;
import com.smartcampus.vendor.VendorLoadReader;
import com.smartcampus.vendor.VendorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * - List screens use the backlog cached for 15 s in Redis; order placement always computes fresh.
 * - While orders are open, their ready time is recomputed when the shop's order set changes, and
 *   OrderEtaUpdated is published only when it moved by 2 minutes or more.
 *
 * VendorService is looked up lazily because the vendor module reads this class through VendorLoadReader.
 */
@Service
@Slf4j
public class EstimateService implements EtaProvider, VendorLoadReader {

    static final Set<OrderStatus> BACKLOG_STATUSES = EnumSet.of(OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.PREPARING);
    static final int ETA_CHANGE_THRESHOLD_MINUTES = 2;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ObjectProvider<VendorService> vendorService;
    private final VendorBacklogCache backlogCache;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.eta.rush-extra-minutes:10}")
    private int rushExtraMinutes;

    @Value("${app.eta.range-factor:1.3}")
    private double rangeFactor;

    public EstimateService(OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           ObjectProvider<VendorService> vendorService,
                           VendorBacklogCache backlogCache,
                           ApplicationEventPublisher eventPublisher,
                           Clock clock) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.vendorService = vendorService;
        this.backlogCache = backlogCache;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

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

    /**
     * Pure function: minutes of cooking still left for one order. PREPARING orders count down from preparing_at.
     */
    public static int remainingOwnMinutes(int ownMinutes, OrderStatus status, Instant preparingAt, Instant now) {
        if (status == OrderStatus.PREPARING && preparingAt != null) {
            long elapsed = Duration.between(preparingAt, now).toMinutes();
            return (int) Math.max(0, ownMinutes - elapsed);
        }
        return ownMinutes;
    }

    /** Fresh backlog from the database (used at order placement and to fill the cache). */
    @Override
    @Transactional(readOnly = true)
    public int getBacklogMinutes(long vendorId) {
        List<Order> activeOrders = orderRepository.findByVendorIdAndStatusIn(vendorId, BACKLOG_STATUSES);
        Map<Long, Integer> own = ownMinutesByOrder(activeOrders);
        Instant now = clock.instant();
        int total = 0;
        for (Order order : activeOrders) {
            total += remainingOwnMinutes(own.getOrDefault(order.getId(), 0), order.getStatus(), order.getPreparingAt(), now);
        }
        return total;
    }

    /** Menu screen: estimate for one dish under the current (cached) load. */
    @Override
    @Transactional(readOnly = true)
    public EtaRange getItemEta(long vendorId, int prepMinutes) {
        Vendor vendor = vendorService.getObject().getVendorEntity(vendorId);
        int backlog = backlogCache.get(vendorId, this::getBacklogMinutes);
        int queueWait = calculateQueueWait(backlog, vendor.getParallelCapacity(), vendor.isRushMode(), rushExtraMinutes);
        return calculateRange(queueWait + prepMinutes, rangeFactor);
    }

    /** Slot screen: a shop-level estimate (vendor average dish time) under the cached load. */
    @Transactional(readOnly = true)
    public EtaRange vendorLevelEstimate(Vendor vendor) {
        int backlog = backlogCache.get(vendor.getId(), this::getBacklogMinutes);
        int queueWait = calculateQueueWait(backlog, vendor.getParallelCapacity(), vendor.isRushMode(), rushExtraMinutes);
        return calculateRange(queueWait + vendor.getAvgPrepMinutes(), rangeFactor);
    }

    /**
     * Calculates fresh promised estimate at order creation time directly from the database.
     */
    @Transactional(readOnly = true)
    public EtaRange calculateOrderPromise(Vendor vendor, List<Integer> itemPrepTimes) {
        int backlog = getBacklogMinutes(vendor.getId());
        int queueWait = calculateQueueWait(backlog, vendor.getParallelCapacity(), vendor.isRushMode(), rushExtraMinutes);
        int ownMinutes = calculateOwnMinutes(itemPrepTimes);
        return calculateRange(queueWait + ownMinutes, rangeFactor);
    }

    /**
     * Recomputes every open order of the shop (queue position by placement time) and publishes
     * OrderEtaUpdated for orders whose expected ready time moved by 2 minutes or more.
     * Runs in its own transaction after the change that triggered it has committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVendorOrderSetChanged(long vendorId) {
        backlogCache.evict(vendorId);
        Vendor vendor = vendorService.getObject().getVendorEntity(vendorId);
        List<Order> open = new ArrayList<>(orderRepository.findByVendorIdAndStatusIn(vendorId, BACKLOG_STATUSES));
        open.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        Map<Long, Integer> own = ownMinutesByOrder(open);
        Instant now = clock.instant();

        int backlogAhead = 0;
        List<OrderEtaUpdated> updates = new ArrayList<>();
        for (Order order : open) {
            int remaining = remainingOwnMinutes(own.getOrDefault(order.getId(), 0), order.getStatus(), order.getPreparingAt(), now);
            int queueWait = calculateQueueWait(backlogAhead, vendor.getParallelCapacity(), vendor.isRushMode(), rushExtraMinutes);
            EtaRange range = calculateRange(queueWait + remaining, rangeFactor);
            Instant newReadyAt = now.plus(Duration.ofMinutes(range.lowMinutes()));

            if (order.getEstReadyAt() == null
                    || Math.abs(Duration.between(order.getEstReadyAt(), newReadyAt).toMinutes()) >= ETA_CHANGE_THRESHOLD_MINUTES) {
                updates.add(new OrderEtaUpdated(order.getId(), vendorId, range.lowMinutes(), range.highMinutes(), newReadyAt, now));
            }
            backlogAhead += remaining;
        }

        for (OrderEtaUpdated update : updates) {
            orderRepository.updateEstReadyAt(update.orderId(), update.estReadyAt());
            eventPublisher.publishEvent(update);
        }
    }

    private Map<Long, Integer> ownMinutesByOrder(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Integer>> prepByOrder = new HashMap<>();
        for (Object[] row : orderItemRepository.findPrepMinutesByOrderIds(orders.stream().map(Order::getId).toList())) {
            long orderId = ((Number) row[0]).longValue();
            int prep = ((Number) row[1]).intValue();
            prepByOrder.computeIfAbsent(orderId, k -> new ArrayList<>()).add(prep);
        }
        Map<Long, Integer> own = new HashMap<>();
        prepByOrder.forEach((orderId, preps) -> own.put(orderId, calculateOwnMinutes(preps)));
        return own;
    }
}
