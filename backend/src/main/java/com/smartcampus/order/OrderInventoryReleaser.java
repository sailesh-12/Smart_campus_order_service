package com.smartcampus.order;

import com.smartcampus.menu.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Rule 5.2: gives back an order's stock and booked pickup slot.
 * Must only be called by the code path that has just won the order's conditional status update
 * (expiry job, outcome handler on FAILED, student cancel, vendor reject), in that same transaction.
 * That guard is what makes the release happen exactly once even when those paths race.
 */
@Component
@RequiredArgsConstructor
class OrderInventoryReleaser {

    private final OrderItemRepository orderItemRepository;
    private final StockService stockService;
    private final SlotService slotService;

    @Transactional(propagation = Propagation.MANDATORY)
    void release(long orderId, long vendorId, Instant pickupSlotStart) {
        // Ascending item id, like placement, so concurrent releases and placements cannot deadlock
        for (OrderItem line : orderItemRepository.findByOrderIdOrderByMenuItemIdAsc(orderId)) {
            stockService.restore(line.getMenuItemId(), line.getQty());
        }
        if (pickupSlotStart != null) {
            slotService.releaseSlot(vendorId, pickupSlotStart);
        }
    }
}
