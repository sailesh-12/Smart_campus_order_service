package com.smartcampus.menu.events;

/**
 * Event published when a menu item's availability or stock status changes.
 * Consumed by SSE / event listeners to push live updates to users browsing the vendor's menu.
 */
public record MenuItemUpdated(
        long vendorId,
        long itemId,
        boolean available,
        boolean lowStock
) {
}
