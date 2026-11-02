package com.smartcampus.order;

import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.order.dto.CreateOrderRequest;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.order.dto.PayOrderResponse;
import com.smartcampus.order.dto.PickupCredentialsResponse;
import com.smartcampus.order.dto.PlaceOrderResult;
import com.smartcampus.order.dto.SlotResponse;
import com.smartcampus.payment.PaymentStatus;
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

    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final OrderService orderService;
    private final PickupService pickupService;
    private final SlotService slotService;
    private final CurrentUser currentUser;

    /** 201 for a new order; 200 + Idempotent-Replayed: true when the same key and cart were sent before. */
    @PostMapping("/orders")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return toResponse(orderService.placeOrder(request, currentUser.id(), idempotencyKey));
    }

    /** 200 paid, 202 PAYMENT_UNCONFIRMED (still being confirmed), 402 PAYMENT_DECLINED (thrown). */
    @PostMapping("/orders/{id}/pay")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PayOrderResponse> payOrder(@PathVariable long id) {
        PayOrderResponse result = orderService.payOrder(id, currentUser.id());
        HttpStatus status = result.paymentStatus() == PaymentStatus.UNCONFIRMED ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/orders/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<OrderResponse>> getMyOrders() {
        return ResponseEntity.ok(orderService.getStudentOrders(currentUser.id()));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'VENDOR', 'ADMIN')")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable long id) {
        return ResponseEntity.ok(orderService.getOrderById(id, currentUser.id(), currentUser.role()));
    }

    @GetMapping("/orders/{id}/pickup")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PickupCredentialsResponse> getPickupDetails(@PathVariable long id) {
        return ResponseEntity.ok(pickupService.getPickupDetails(id, currentUser.id()));
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id, currentUser.id()));
    }

    @PostMapping("/orders/{id}/reorder")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<OrderResponse> reorder(
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return toResponse(orderService.reorder(id, currentUser.id(), idempotencyKey));
    }

    @GetMapping("/vendors/{id}/slots")
    public ResponseEntity<List<SlotResponse>> getSlots(
            @PathVariable long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(slotService.getSlotsForVendor(id, date));
    }

    private static ResponseEntity<OrderResponse> toResponse(PlaceOrderResult result) {
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(result.order());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.order());
    }
}
