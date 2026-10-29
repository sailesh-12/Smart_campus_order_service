package com.smartcampus.menu;

import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.menu.dto.CreateMenuItemRequest;
import com.smartcampus.menu.dto.MenuItemStudentResponse;
import com.smartcampus.menu.dto.MenuItemVendorResponse;
import com.smartcampus.menu.dto.UpdateAvailabilityRequest;
import com.smartcampus.menu.dto.UpdateMenuItemRequest;
import com.smartcampus.menu.dto.UpdateStockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final CurrentUser currentUser;

    @GetMapping("/vendors/{id}/menu")
    public ResponseEntity<List<MenuItemStudentResponse>> getMenuForStudents(@PathVariable long id) {
        return ResponseEntity.ok(menuService.getMenuForStudents(id));
    }

    @GetMapping("/vendor/menu")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<List<MenuItemVendorResponse>> getMyMenu() {
        return ResponseEntity.ok(menuService.getMenuForVendor(currentUser.id()));
    }

    @PostMapping("/menu-items")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<MenuItemVendorResponse> createMenuItem(@Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createMenuItem(request, currentUser.id()));
    }

    @PutMapping("/menu-items/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<MenuItemVendorResponse> updateMenuItem(
            @PathVariable long id,
            @Valid @RequestBody UpdateMenuItemRequest request) {
        return ResponseEntity.ok(menuService.updateMenuItem(id, request, currentUser.id()));
    }

    @PatchMapping("/menu-items/{id}/availability")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<MenuItemVendorResponse> updateAvailability(
            @PathVariable long id,
            @Valid @RequestBody UpdateAvailabilityRequest request) {
        return ResponseEntity.ok(menuService.updateAvailability(id, request.isAvailable(), currentUser.id()));
    }

    @PatchMapping("/menu-items/{id}/stock")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<MenuItemVendorResponse> updateStock(
            @PathVariable long id,
            @Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(menuService.updateStock(id, request.stockQty(), currentUser.id()));
    }

    @DeleteMapping("/menu-items/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable long id) {
        menuService.deleteMenuItem(id, currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
