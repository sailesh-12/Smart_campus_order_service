package com.smartcampus.menu;

import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.menu.dto.CreateMenuItemRequest;
import com.smartcampus.menu.dto.MenuItemStudentResponse;
import com.smartcampus.menu.dto.MenuItemVendorResponse;
import com.smartcampus.menu.dto.UpdateMenuItemRequest;
import com.smartcampus.vendor.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuItemRepository menuItemRepository;
    private final StockService stockService;
    private final EtaProvider etaProvider;
    private final VendorService vendorService;

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemStudentResponse> getMenuForStudents(long vendorId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemVendorResponse> getMenuForVendor(long requestingUserId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public MenuItemVendorResponse createMenuItem(CreateMenuItemRequest request, long requestingUserId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public MenuItemVendorResponse updateMenuItem(long itemId, UpdateMenuItemRequest request, long requestingUserId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public MenuItemVendorResponse updateAvailability(long itemId, boolean available, long requestingUserId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public MenuItemVendorResponse updateStock(long itemId, int stockQty, long requestingUserId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public void deleteMenuItem(long itemId, long requestingUserId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItem getMenuItemEntity(long itemId) {
        return menuItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Menu item not found with id: " + itemId));
    }
}
