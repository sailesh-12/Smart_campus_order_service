package com.smartcampus.order;

import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.util.RateLimiter;
import com.smartcampus.order.dto.CreateOrderRequest;
import com.smartcampus.order.dto.OrderItemRequest;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.order.dto.PlaceOrderResult;
import com.smartcampus.payment.PaymentService;
import com.smartcampus.vendor.VendorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for idempotency key handling (Section 5.7), fully implemented with mocks:
 * the replay decision, the payload comparison and the race (unique-violation) path.
 */
class IdempotencyKeyTest {

    private static final long STUDENT = 7L;
    private static final long VENDOR = 3L;
    private static final String KEY = "key-123";

    private OrderRepository orderRepository;
    private OrderPlacement orderPlacement;
    private OrderMapper orderMapper;
    private RateLimiter rateLimiter;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderPlacement = mock(OrderPlacement.class);
        orderMapper = mock(OrderMapper.class);
        rateLimiter = mock(RateLimiter.class);
        service = new OrderServiceImpl(orderRepository, orderPlacement, mock(OrderInventoryReleaser.class), orderMapper,
                mock(PaymentService.class), mock(VendorService.class), rateLimiter,
                mock(ApplicationEventPublisher.class), Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneId.of("UTC")));
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(inv -> response(inv.getArgument(0)));
    }

    @Test
    void sameKey_samePayload_returnsExistingOrder() {
        Order existing = existingOrder(List.of(line(10L, 2), line(11L, 1)));
        stubExisting(existing);

        PlaceOrderResult result = service.placeOrder(request(new OrderItemRequest(11L, 1), new OrderItemRequest(10L, 2)), STUDENT, KEY);

        assertTrue(result.replayed());
        assertEquals(existing.getId(), result.order().id());
        verifyNoInteractions(orderPlacement);
        // Replays are exempt from the order rate limit and the hoarding limit
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void sameKey_duplicateLinesMerged_isStillTheSameRequest() {
        Order existing = existingOrder(List.of(line(10L, 3)));
        stubExisting(existing);

        PlaceOrderResult result = service.placeOrder(request(new OrderItemRequest(10L, 1), new OrderItemRequest(10L, 2)), STUDENT, KEY);

        assertTrue(result.replayed());
    }

    @Test
    void differentKeys_createDifferentOrders() {
        when(orderRepository.findByStudentIdAndIdempotencyKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(orderPlacement.place(any(), eq(STUDENT), anyString()))
                .thenReturn(orderResponse(100L), orderResponse(101L));

        PlaceOrderResult first = service.placeOrder(request(new OrderItemRequest(10L, 1)), STUDENT, "key-a");
        PlaceOrderResult second = service.placeOrder(request(new OrderItemRequest(10L, 1)), STUDENT, "key-b");

        assertFalse(first.replayed());
        assertFalse(second.replayed());
        assertNotEquals(first.order().id(), second.order().id());
        verify(rateLimiter, times(2)).checkLimit(eq("rate:order:"), eq(String.valueOf(STUDENT)), anyInt(), eq(true));
    }

    @Test
    void sameKey_differentPayload_returns409() {
        stubExisting(existingOrder(List.of(line(10L, 2))));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.placeOrder(request(new OrderItemRequest(10L, 3)), STUDENT, KEY));

        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.getErrorCode());
        verifyNoInteractions(orderPlacement);
    }

    @Test
    void twoIdenticalRequests_racing_oneWinsOtherReturnsExisting() {
        Order winner = existingOrder(List.of(line(10L, 2)));
        // Not there when this request checks, there once the unique index rejects our insert
        when(orderRepository.findByStudentIdAndIdempotencyKey(STUDENT, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(orderRepository.findWithItemsById(winner.getId())).thenReturn(Optional.of(winner));
        when(orderPlacement.place(any(), eq(STUDENT), eq(KEY)))
                .thenThrow(new DataIntegrityViolationException("uq_order_idempotency"));

        PlaceOrderResult result = service.placeOrder(request(new OrderItemRequest(10L, 2)), STUDENT, KEY);

        assertTrue(result.replayed());
        assertEquals(winner.getId(), result.order().id());
    }

    @Test
    void blankKey_isRejectedWith400() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.placeOrder(request(new OrderItemRequest(10L, 1)), STUDENT, "  "));
        assertEquals(400, ex.getStatus().value());
        verifyNoInteractions(orderPlacement);
    }

    @Test
    void fingerprint_ignoresLineOrder_butNotSlotOrVendor() {
        OrderFingerprint a = OrderFingerprint.of(new CreateOrderRequest(VENDOR,
                List.of(new OrderItemRequest(1L, 1), new OrderItemRequest(2L, 2)), null));
        OrderFingerprint b = OrderFingerprint.of(new CreateOrderRequest(VENDOR,
                List.of(new OrderItemRequest(2L, 2), new OrderItemRequest(1L, 1)), null));
        OrderFingerprint otherSlot = OrderFingerprint.of(new CreateOrderRequest(VENDOR,
                List.of(new OrderItemRequest(1L, 1), new OrderItemRequest(2L, 2)), Instant.parse("2026-10-23T07:00:00Z")));
        OrderFingerprint otherVendor = OrderFingerprint.of(new CreateOrderRequest(VENDOR + 1,
                List.of(new OrderItemRequest(1L, 1), new OrderItemRequest(2L, 2)), null));

        assertTrue(a.sameRequestAs(b));
        assertFalse(a.sameRequestAs(otherSlot));
        assertFalse(a.sameRequestAs(otherVendor));
    }

    // ---------- helpers

    private void stubExisting(Order existing) {
        when(orderRepository.findByStudentIdAndIdempotencyKey(STUDENT, KEY)).thenReturn(Optional.of(existing));
        when(orderRepository.findWithItemsById(existing.getId())).thenReturn(Optional.of(existing));
    }

    private static CreateOrderRequest request(OrderItemRequest... lines) {
        return new CreateOrderRequest(VENDOR, List.of(lines), null);
    }

    private static OrderItem line(long itemId, int qty) {
        return OrderItem.builder().menuItemId(itemId).itemName("item " + itemId).qty(qty).unitPrice(BigDecimal.TEN).build();
    }

    private static Order existingOrder(List<OrderItem> lines) {
        Order order = Order.builder().id(55L).studentId(STUDENT).vendorId(VENDOR)
                .status(OrderStatus.PENDING_PAYMENT).idempotencyKey(KEY).total(BigDecimal.TEN)
                .items(new ArrayList<>(lines)).build();
        lines.forEach(l -> l.setOrder(order));
        return order;
    }

    private static OrderResponse response(Order order) {
        return new OrderResponse(order.getId(), order.getStudentId(), order.getVendorId(), order.getStatus(),
                order.getTotal(), null, null, null, null, null, null, null, List.of());
    }

    private static OrderResponse orderResponse(long id) {
        return new OrderResponse(id, STUDENT, VENDOR, OrderStatus.PENDING_PAYMENT, BigDecimal.TEN,
                null, null, null, null, null, null, 1L, List.of());
    }
}
