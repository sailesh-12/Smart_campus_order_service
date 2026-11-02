package com.smartcampus.menu;

import com.smartcampus.menu.events.MenuItemUpdated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing inventory and stock reservations.
 * Business Rules (Section 5.1 & 5.2):
 * - StockService.tryDecrement(itemId, qty) executes ONE conditional update:
 *   decreases stock_qty only where stock_qty >= qty. Returns true if exactly 1 row changed.
 * - StockService.restore(itemId, qty) adds stock back and publishes MenuItemUpdated.
 * - Stock is released EXACTLY ONCE per order guarded by the order status transition.
 * - Deadlock prevention: Order placement processes items in ascending menu_item_id order.
 * - "Available" in DTOs = is_available AND stock_qty > 0.
 * - lowStock = stock at or below app.menu.low-stock-threshold.
 * - Never expose exact stock quantity to student clients.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final MenuItemRepository menuItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.menu.low-stock-threshold:5}")
    private int lowStockThreshold;

    /**
     * Executes atomic conditional decrement directly in SQL.
     * Never reads stock in Java and then writes it.
     *
     * @param itemId menu item id
     * @param qty quantity to decrement
     * @return true if decrement succeeded (exactly 1 row changed), false otherwise
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryDecrement(long itemId, int qty) {
        int updated = menuItemRepository.tryDecrementStock(itemId, qty);
        if (updated == 1) {
            publishUpdatedState(itemId);
            return true;
        }
        return false;
    }

    /**
     * Restores stock to the menu item and publishes MenuItemUpdated event.
     * Callers must only invoke this after winning the order's conditional status update (rule 5.2).
     *
     * @param itemId menu item id
     * @param qty quantity to restore
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(long itemId, int qty) {
        int updated = menuItemRepository.incrementStock(itemId, qty);
        if (updated > 0) {
            publishUpdatedState(itemId);
        }
    }

    /**
     * Publishes a MenuItemUpdated event with availability and lowStock flags.
     * The event is delivered to SSE clients after the surrounding transaction commits.
     */
    public void publishUpdatedState(long itemId) {
        menuItemRepository.findById(itemId).ifPresent(item -> {
            // The entity may be stale after the bulk update, so read the stock column directly
            Integer stock = menuItemRepository.currentStock(itemId);
            int stockQty = stock != null ? stock : item.getStockQty();
            publish(item.getVendorId(), item.getId(), item.isAvailable(), stockQty);
        });
    }

    public void publish(long vendorId, long itemId, boolean isAvailable, int stockQty) {
        eventPublisher.publishEvent(new MenuItemUpdated(vendorId, itemId,
                isAvailable(isAvailable, stockQty), isLowStock(stockQty)));
    }

    public boolean isAvailable(boolean isAvailableFlag, int stockQty) {
        return isAvailableFlag && stockQty > 0;
    }

    public boolean isLowStock(int stockQty) {
        return stockQty > 0 && stockQty <= lowStockThreshold;
    }
}
