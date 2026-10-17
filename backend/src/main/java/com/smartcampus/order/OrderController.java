package com.smartcampus.order;

import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.order.dto.CreateOrderRequest;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.order.dto.PayOrderResponse;
import com.smartcampus.order.dto.PickupCredentialsResponse;
import com.smartcampus.order.dto.SlotResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PickupService pickupService;
    private final SlotService slotService;
    private final CurrentUser currentUser;

    @PostMapping("/orders")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/orders/{id}/pay")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PayOrderResponse> payOrder(@PathVariable long id) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/orders/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<OrderResponse>> getMyOrders() {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'VENDOR', 'ADMIN')")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable long id) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/orders/{id}/pickup")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PickupCredentialsResponse> getPickupDetails(@PathVariable long id) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> cancelOrder(@PathVariable long id) {
        throw new UnsupportedOperationException("TODO");
    }

    @PostMapping("/orders/{id}/reorder")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<OrderResponse> reorder(
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/vendors/{id}/slots")
    public ResponseEntity<List<SlotResponse>> getSlots(
            @PathVariable long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        throw new UnsupportedOperationException("TODO");
    }
}
