package com.smartcampus.order;

import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.exception.ForbiddenException;
import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.common.util.RateLimiter;
import com.smartcampus.order.dto.CreateOrderRequest;
import com.smartcampus.order.dto.OrderItemRequest;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.order.dto.PayOrderResponse;
import com.smartcampus.order.dto.PlaceOrderResult;
import com.smartcampus.order.dto.VendorOrderStats;
import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.Payment;
import com.smartcampus.payment.PaymentOutcome;
import com.smartcampus.payment.PaymentService;
import com.smartcampus.payment.PaymentStatus;
import com.smartcampus.payment.RefundReason;
import com.smartcampus.vendor.VendorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    /** Vendor board columns (UNCOLLECTED is hidden from them, rule 5.11). */
    static final Set<OrderStatus> ACTIVE_BOARD = EnumSet.of(OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY);
    /** Statuses a vendor may set through PATCH /orders/{id}/status. */
    static final Set<OrderStatus> VENDOR_TARGETS = EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.REJECTED);
    /** Orders whose payment went through and that still count as sales. */
    static final Set<OrderStatus> REVENUE = EnumSet.of(OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.PREPARING,
            OrderStatus.READY, OrderStatus.COLLECTED, OrderStatus.UNCOLLECTED);
    static final Set<OrderStatus> CLOSED_UNSOLD = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.EXPIRED);

    private final OrderRepository orderRepository;
    private final OrderPlacement orderPlacement;
    private final OrderInventoryReleaser inventoryReleaser;
    private final OrderMapper orderMapper;
    private final PaymentService paymentService;
    private final VendorService vendorService;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.order.rate-limit-per-minute:5}")
    private int orderRateLimitPerMinute;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderPlacement orderPlacement,
                            OrderInventoryReleaser inventoryReleaser,
                            OrderMapper orderMapper,
                            @Lazy PaymentService paymentService,
                            VendorService vendorService,
                            RateLimiter rateLimiter,
                            ApplicationEventPublisher eventPublisher,
                            Clock clock) {
        this.orderRepository = orderRepository;
        this.orderPlacement = orderPlacement;
        this.inventoryReleaser = inventoryReleaser;
        this.orderMapper = orderMapper;
        this.paymentService = paymentService;
        this.vendorService = vendorService;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ placement (5.1, 5.7, 5.10)

    @Override
    public PlaceOrderResult placeOrder(CreateOrderRequest request, long studentId, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 64) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Idempotency-Key", "Idempotency-Key must be 1 to 64 characters");
        }
        OrderFingerprint cart = OrderFingerprint.of(request);

        // Replays are answered first: they are exempt from the hoarding and rate limits
        var existing = findExisting(studentId, key);
        if (existing != null) {
            return replay(existing, cart);
        }

        rateLimiter.checkLimit("rate:order:", String.valueOf(studentId), orderRateLimitPerMinute, true);

        try {
            return new PlaceOrderResult(orderPlacement.place(cart, studentId, key), false);
        } catch (DataIntegrityViolationException e) {
            // Two identical requests raced; the other one won the unique (student_id, idempotency_key)
            Order winner = findExisting(studentId, key);
            if (winner == null) {
                throw e;
            }
            return replay(winner, cart);
        }
    }

    @Override
    public PlaceOrderResult reorder(long orderId, long studentId, String idempotencyKey) {
        Order previous = loadWithItems(orderId);
        requireStudentOwner(previous, studentId);
        List<OrderItemRequest> lines = previous.getItems().stream()
                .map(i -> new OrderItemRequest(i.getMenuItemId(), i.getQty()))
                .toList();
        return placeOrder(new CreateOrderRequest(previous.getVendorId(), lines, null), studentId, idempotencyKey);
    }

    private Order findExisting(long studentId, String key) {
        return orderRepository.findByStudentIdAndIdempotencyKey(studentId, key)
                .flatMap(o -> orderRepository.findWithItemsById(o.getId()))
                .orElse(null);
    }

    private PlaceOrderResult replay(Order existing, OrderFingerprint cart) {
        if (!OrderFingerprint.of(existing).sameRequestAs(cart)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different order");
        }
        return new PlaceOrderResult(orderMapper.toResponse(existing), true);
    }

    // ------------------------------------------------------------------ payment step 2 (5.3)

    @Override
    public PayOrderResponse payOrder(long orderId, long studentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
        requireStudentOwner(order, studentId);
        Payment payment = paymentService.getPaymentByOrderId(orderId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            if (payment.getStatus() == PaymentStatus.SUCCESS && REVENUE.contains(order.getStatus())) {
                return new PayOrderResponse(orderId, payment.getStatus(), order.getStatus(), "Order is already paid");
            }
            throw new ConflictException("INVALID_TRANSITION", "Order is " + order.getStatus() + " and cannot be paid");
        }
        if (payment.getStatus() == PaymentStatus.UNCONFIRMED) {
            return unconfirmed(orderId);
        }
        if (order.getHoldExpiresAt() != null && !clock.instant().isBefore(order.getHoldExpiresAt())) {
            throw new ConflictException("HOLD_EXPIRED", "The hold on this order has expired. Please order again.");
        }

        PaymentOutcome outcome = paymentService.processCharge(payment.getId());

        OrderStatus status = orderRepository.findById(orderId).map(Order::getStatus).orElse(order.getStatus());
        return switch (outcome) {
            case SUCCESS -> new PayOrderResponse(orderId, PaymentStatus.SUCCESS, status, "Payment successful");
            case FAILED -> throw new ApiException("PAYMENT_DECLINED", HttpStatus.PAYMENT_REQUIRED,
                    "Payment was declined. The order was cancelled and nothing was charged.");
            case UNKNOWN -> unconfirmed(orderId);
        };
    }

    private PayOrderResponse unconfirmed(long orderId) {
        return new PayOrderResponse(orderId, PaymentStatus.UNCONFIRMED, OrderStatus.PENDING_PAYMENT,
                "PAYMENT_UNCONFIRMED: we are confirming your payment with the bank. You will not be charged twice.");
    }

    // ------------------------------------------------------------------ cancel / vendor transitions (5.6)

    @Override
    @Transactional
    public OrderResponse cancelOrder(long orderId, long studentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
        requireStudentOwner(order, studentId);
        Instant now = clock.instant();

        switch (order.getStatus()) {
            case PENDING_PAYMENT -> {
                Payment payment = paymentService.getPaymentByOrderId(orderId);
                if (payment.getStatus() == PaymentStatus.UNCONFIRMED) {
                    throw new ConflictException("PAYMENT_UNCONFIRMED",
                            "Your payment is still being confirmed, so the order cannot be cancelled yet");
                }
                // The payment stays INITIATED: if a charge still lands, it is a late success and refunded (5.4)
                transitionOrFail(order, OrderStatus.CANCELLED, now);
                inventoryReleaser.release(order.getId(), order.getVendorId(), order.getPickupSlotStart());
            }
            case PAID -> {
                transitionOrFail(order, OrderStatus.CANCELLED, now);
                inventoryReleaser.release(order.getId(), order.getVendorId(), order.getPickupSlotStart());
                Payment payment = paymentService.getPaymentByOrderId(orderId);
                paymentService.requestRefundAfterCommit(payment.getId(), RefundReason.CANCELLED);
            }
            default -> throw new ConflictException("INVALID_TRANSITION",
                    "Order is " + order.getStatus() + " and can no longer be cancelled");
        }
        eventPublisher.publishEvent(new OrderStatusChanged(order.getId(), order.getVendorId(), OrderStatus.CANCELLED, now));
        return orderMapper.toResponse(loadWithItems(orderId));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(long orderId, OrderStatus newStatus, long vendorUserId) {
        long vendorId = vendorService.vendorIdOf(vendorUserId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
        if (!order.getVendorId().equals(vendorId)) {
            throw new ForbiddenException("This order belongs to another shop");
        }
        OrderStatus current = order.getStatus();
        if (!VENDOR_TARGETS.contains(newStatus)) {
            throw new ConflictException("INVALID_TRANSITION",
                    newStatus + " cannot be set by hand (COLLECTED: use pickup verification)");
        }
        if (!current.canTransitionTo(newStatus)) {
            throw new ConflictException("INVALID_TRANSITION", "Cannot move an order from " + current + " to " + newStatus);
        }

        Instant now = clock.instant();
        int updated = switch (newStatus) {
            case ACCEPTED -> orderRepository.markAccepted(orderId, current, now);
            case PREPARING -> orderRepository.markPreparing(orderId, current, now);
            case READY -> orderRepository.markReady(orderId, current, now);
            default -> orderRepository.updateStatusConditional(orderId, newStatus, current, now);
        };
        if (updated == 0) {
            throw new ConflictException("INVALID_TRANSITION", "The order was changed meanwhile; reload and try again");
        }

        if (newStatus == OrderStatus.REJECTED) {
            inventoryReleaser.release(order.getId(), order.getVendorId(), order.getPickupSlotStart());
            Payment payment = paymentService.getPaymentByOrderId(orderId);
            paymentService.requestRefundAfterCommit(payment.getId(), RefundReason.REJECTED);
        }
        eventPublisher.publishEvent(new OrderStatusChanged(orderId, vendorId, newStatus, now));
        return orderMapper.toResponse(loadWithItems(orderId));
    }

    private void transitionOrFail(Order order, OrderStatus target, Instant now) {
        if (orderRepository.updateStatusConditional(order.getId(), target, order.getStatus(), now) == 0) {
            throw new ConflictException("INVALID_TRANSITION", "The order was changed meanwhile; reload and try again");
        }
    }

    // ------------------------------------------------------------------ reads

    @Override
    @Transactional(readOnly = true)
    public OrderStatusChanged currentStatusEvent(long orderId, long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));

        if (!order.getStudentId().equals(userId)) {
            throw new ForbiddenException("Cannot subscribe to events for another student's order");
        }

        return new OrderStatusChanged(order.getId(), order.getVendorId(), order.getStatus(), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(long orderId, long userId, String role) {
        Order order = loadWithItems(orderId);
        switch (role == null ? "" : role) {
            case "ADMIN" -> { }
            case "VENDOR" -> {
                if (!order.getVendorId().equals(vendorService.vendorIdOf(userId))) {
                    throw new ForbiddenException("This order belongs to another shop");
                }
            }
            case "STUDENT" -> requireStudentOwner(order, userId);
            default -> throw new ForbiddenException("Not allowed");
        }
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getStudentOrders(long studentId) {
        return orderMapper.toResponses(orderRepository.findByStudentIdOrderByCreatedAtDesc(studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getVendorOrders(long vendorId, OrderStatus status) {
        Set<OrderStatus> statuses = status == null ? ACTIVE_BOARD : EnumSet.of(status);
        return orderMapper.toResponses(orderRepository.findByVendorIdAndStatusInOrderByCreatedAtAsc(vendorId, statuses));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getRecentOrders(int limit) {
        return orderMapper.toResponses(orderRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public long studentIdOf(long orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getStudentId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatus statusOf(long orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getStatus)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public VendorOrderStats getVendorStats(long vendorId, Instant from, Instant to, int topItems) {
        BigDecimal revenue = orderRepository.sumTotalForVendor(vendorId, REVENUE, from, to);
        long total = orderRepository.countForVendor(vendorId, EnumSet.allOf(OrderStatus.class), from, to);
        long completed = orderRepository.countForVendor(vendorId, EnumSet.of(OrderStatus.COLLECTED), from, to);
        long cancelled = orderRepository.countForVendor(vendorId, CLOSED_UNSOLD, from, to);
        List<VendorOrderStats.TopItem> top = orderRepository
                .topItemsForVendor(vendorId, REVENUE, from, to, PageRequest.of(0, topItems)).stream()
                .map(row -> new VendorOrderStats.TopItem(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        (BigDecimal) row[3]))
                .toList();
        return new VendorOrderStats(revenue, total, completed, cancelled, top);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal totalRevenue() {
        return orderRepository.sumTotal(REVENUE);
    }

    @Override
    @Transactional(readOnly = true)
    public long totalOrders() {
        return orderRepository.count();
    }

    private Order loadWithItems(long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));
    }

    private static void requireStudentOwner(Order order, long studentId) {
        if (!order.getStudentId().equals(studentId)) {
            throw new ForbiddenException("This order belongs to another student");
        }
    }
}
