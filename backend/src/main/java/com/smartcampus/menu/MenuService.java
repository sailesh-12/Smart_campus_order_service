package com.smartcampus.menu;

import com.smartcampus.menu.dto.CreateMenuItemRequest;
import com.smartcampus.menu.dto.MenuItemStudentResponse;
import com.smartcampus.menu.dto.MenuItemVendorResponse;
import com.smartcampus.menu.dto.UpdateMenuItemRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Service managing menu items, categories, and availability displays.
 */
public interface MenuService {

    /**
     * Student view: every item of the vendor (unavailable ones greyed out), never the exact stock,
     * plus the per-item estimate range under the vendor's current load (Section 6).
     */
    List<MenuItemStudentResponse> getMenuForStudents(long vendorId);

    /** Vendor view of their own menu, with exact stock. */
    List<MenuItemVendorResponse> getMenuForVendor(long requestingUserId);

    MenuItemVendorResponse createMenuItem(CreateMenuItemRequest request, long requestingUserId);

    MenuItemVendorResponse updateMenuItem(long itemId, UpdateMenuItemRequest request, long requestingUserId);

    MenuItemVendorResponse updateAvailability(long itemId, boolean available, long requestingUserId);

    MenuItemVendorResponse updateStock(long itemId, int stockQty, long requestingUserId);

    /**
     * Deletes an item that was never ordered. Items that appear in past orders are kept for order history;
     * the vendor gets 409 ITEM_IN_USE and should mark them unavailable instead.
     */
    void deleteMenuItem(long itemId, long requestingUserId);

    MenuItem getMenuItemEntity(long itemId);

    /** Items for order placement, in ascending id order (rule 5.1: deadlock prevention). */
    List<MenuItem> getItemsByIdsAscending(Collection<Long> itemIds);

    /** prep_minutes per item id, used by the order module's ETA calculation. */
    Map<Long, Integer> getPrepMinutes(Collection<Long> itemIds);
}
