package com.smartcampus.menu;

import com.smartcampus.menu.dto.CreateMenuItemRequest;
import com.smartcampus.menu.dto.MenuItemStudentResponse;
import com.smartcampus.menu.dto.MenuItemVendorResponse;
import com.smartcampus.menu.dto.UpdateMenuItemRequest;

import java.util.List;

/**
 * Service managing menu items, categories, and availability displays.
 */
public interface MenuService {

    List<MenuItemStudentResponse> getMenuForStudents(long vendorId);

    List<MenuItemVendorResponse> getMenuForVendor(long requestingUserId);

    MenuItemVendorResponse createMenuItem(CreateMenuItemRequest request, long requestingUserId);

    MenuItemVendorResponse updateMenuItem(long itemId, UpdateMenuItemRequest request, long requestingUserId);

    MenuItemVendorResponse updateAvailability(long itemId, boolean available, long requestingUserId);

    MenuItemVendorResponse updateStock(long itemId, int stockQty, long requestingUserId);

    void deleteMenuItem(long itemId, long requestingUserId);

    MenuItem getMenuItemEntity(long itemId);
}
