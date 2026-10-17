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
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/vendor/menu")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<List<MenuItemVendorResponse>> getMyMenu() {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/menu-items")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<MenuItemVendorResponse> createMenuItem(@Valid @RequestBody CreateMenuItemRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @PutMapping("/menu-items/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<MenuItemVendorResponse> updateMenuItem(
            @PathVariable long id,
            @Valid @RequestBody UpdateMenuItemRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @PatchMapping("/menu-items/{id}/availability")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<MenuItemVendorResponse> updateAvailability(
            @PathVariable long id,
            @Valid @RequestBody UpdateAvailabilityRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @PatchMapping("/menu-items/{id}/stock")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<MenuItemVendorResponse> updateStock(
            @PathVariable long id,
            @Valid @RequestBody UpdateStockRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @DeleteMapping("/menu-items/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable long id) {
        throw new UnsupportedOperationException("TODO");
    }
}
