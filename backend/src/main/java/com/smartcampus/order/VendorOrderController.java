package com.smartcampus.order;

import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.order.dto.UpdateOrderStatusRequest;
import com.smartcampus.order.dto.VerifyPickupRequest;
import com.smartcampus.vendor.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VendorOrderController {

    private final OrderService orderService;
    private final PickupService pickupService;
    private final VendorService vendorService;
    private final CurrentUser currentUser;

    @GetMapping("/vendor/orders")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<List<OrderResponse>> getVendorOrders(
            @RequestParam(required = false) OrderStatus status) {
        throw new UnsupportedOperationException("TODO");
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> updateOrderStatus(
            @PathVariable long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/orders/pickup/verify")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> verifyPickup(@Valid @RequestBody VerifyPickupRequest request) {
        throw new UnsupportedOperationException("TODO");
    }
}
