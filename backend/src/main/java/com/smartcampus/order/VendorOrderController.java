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

    /** Without ?status the active board (PAID, ACCEPTED, PREPARING, READY) is returned. */
    @GetMapping("/vendor/orders")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<List<OrderResponse>> getVendorOrders(
            @RequestParam(required = false) OrderStatus status) {
        long vendorId = vendorService.vendorIdOf(currentUser.id());
        return ResponseEntity.ok(orderService.getVendorOrders(vendorId, status));
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request.status(), currentUser.id()));
    }

    @PostMapping("/orders/pickup/verify")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<OrderResponse> verifyPickup(@Valid @RequestBody VerifyPickupRequest request) {
        long vendorId = vendorService.vendorIdOf(currentUser.id());
        long orderId = pickupService.verifyPickup(vendorId, request);
        return ResponseEntity.ok(orderService.getOrderById(orderId, currentUser.id(), "VENDOR"));
    }
}
