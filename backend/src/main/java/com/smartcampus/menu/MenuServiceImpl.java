package com.smartcampus.menu;

import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.exception.ForbiddenException;
import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.menu.dto.CreateMenuItemRequest;
import com.smartcampus.menu.dto.EtaRange;
import com.smartcampus.menu.dto.MenuItemStudentResponse;
import com.smartcampus.menu.dto.MenuItemVendorResponse;
import com.smartcampus.menu.dto.UpdateMenuItemRequest;
import com.smartcampus.vendor.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        vendorService.getVendorEntity(vendorId); // 404 for an unknown vendor
        return menuItemRepository.findByVendorIdOrderByCategoryAscNameAsc(vendorId).stream()
                .map(item -> {
                    EtaRange eta = etaProvider.getItemEta(vendorId, item.getPrepMinutes());
                    return new MenuItemStudentResponse(
                            item.getId(),
                            item.getVendorId(),
                            item.getName(),
                            item.getPrice(),
                            stockService.isAvailable(item.isAvailable(), item.getStockQty()),
                            stockService.isLowStock(item.getStockQty()),
                            item.getDescription(),
                            item.getCategory(),
                            item.getImageUrl(),
                            item.getPrepMinutes(),
                            eta);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemVendorResponse> getMenuForVendor(long requestingUserId) {
        long vendorId = vendorService.vendorIdOf(requestingUserId);
        return menuItemRepository.findByVendorIdOrderByCategoryAscNameAsc(vendorId).stream()
                .map(this::toVendorResponse)
                .toList();
    }

    @Override
    @Transactional
    public MenuItemVendorResponse createMenuItem(CreateMenuItemRequest request, long requestingUserId) {
        long vendorId = vendorService.vendorIdOf(requestingUserId);
        MenuItem item = MenuItem.builder()
                .vendorId(vendorId)
                .name(request.name().trim())
                .price(request.price())
                .stockQty(request.stockQty() != null ? request.stockQty() : 0)
                .isAvailable(request.isAvailable() == null || request.isAvailable())
                .description(request.description())
                .category(request.category())
                .imageUrl(request.imageUrl())
                .prepMinutes(request.prepMinutes() != null ? request.prepMinutes() : 10)
                .build();
        item = menuItemRepository.save(item);
        publish(item);
        return toVendorResponse(item);
    }

    @Override
    @Transactional
    public MenuItemVendorResponse updateMenuItem(long itemId, UpdateMenuItemRequest request, long requestingUserId) {
        MenuItem item = getOwnedItem(itemId, requestingUserId);
        item.setName(request.name().trim());
        item.setPrice(request.price());
        if (request.stockQty() != null) {
            item.setStockQty(request.stockQty());
        }
        if (request.isAvailable() != null) {
            item.setAvailable(request.isAvailable());
        }
        item.setDescription(request.description());
        item.setCategory(request.category());
        item.setImageUrl(request.imageUrl());
        if (request.prepMinutes() != null) {
            item.setPrepMinutes(request.prepMinutes());
        }
        item = menuItemRepository.save(item);
        publish(item);
        return toVendorResponse(item);
    }

    @Override
    @Transactional
    public MenuItemVendorResponse updateAvailability(long itemId, boolean available, long requestingUserId) {
        MenuItem item = getOwnedItem(itemId, requestingUserId);
        item.setAvailable(available);
        item = menuItemRepository.save(item);
        publish(item);
        return toVendorResponse(item);
    }

    /**
     * Sets the absolute stock level (a vendor restocking or correcting a count).
     * Stock already reserved by open orders has been subtracted at placement, so this is the free stock.
     */
    @Override
    @Transactional
    public MenuItemVendorResponse updateStock(long itemId, int stockQty, long requestingUserId) {
        MenuItem item = getOwnedItem(itemId, requestingUserId);
        item.setStockQty(stockQty);
        item = menuItemRepository.save(item);
        publish(item);
        return toVendorResponse(item);
    }

    @Override
    @Transactional
    public void deleteMenuItem(long itemId, long requestingUserId) {
        MenuItem item = getOwnedItem(itemId, requestingUserId);
        if (menuItemRepository.countOrderLines(itemId) > 0) {
            throw new ConflictException("ITEM_IN_USE",
                    "This item appears in past orders and cannot be deleted. Mark it unavailable instead.");
        }
        menuItemRepository.delete(item);
        // Tell open menu screens the item is gone (shown as unavailable until they refresh)
        stockService.publish(item.getVendorId(), item.getId(), false, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItem getMenuItemEntity(long itemId) {
        return menuItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Menu item not found with id: " + itemId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItem> getItemsByIdsAscending(Collection<Long> itemIds) {
        return menuItemRepository.findByIdInOrderByIdAsc(itemIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> getPrepMinutes(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return menuItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, MenuItem::getPrepMinutes));
    }

    /** Ownership rule (Section 7): a vendor may only read or change their own menu. */
    private MenuItem getOwnedItem(long itemId, long requestingUserId) {
        MenuItem item = getMenuItemEntity(itemId);
        long vendorId = vendorService.vendorIdOf(requestingUserId);
        if (!item.getVendorId().equals(vendorId)) {
            throw new ForbiddenException("You can only change items on your own menu");
        }
        return item;
    }

    private void publish(MenuItem item) {
        stockService.publish(item.getVendorId(), item.getId(), item.isAvailable(), item.getStockQty());
    }

    private MenuItemVendorResponse toVendorResponse(MenuItem item) {
        return new MenuItemVendorResponse(
                item.getId(),
                item.getVendorId(),
                item.getName(),
                item.getPrice(),
                item.getStockQty(),
                item.isAvailable(),
                stockService.isLowStock(item.getStockQty()),
                item.getDescription(),
                item.getCategory(),
                item.getImageUrl(),
                item.getPrepMinutes());
    }
}
