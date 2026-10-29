package com.smartcampus.order;

import com.smartcampus.common.exception.ApiException;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.exception.ForbiddenException;
import com.smartcampus.menu.MenuItem;
import com.smartcampus.menu.MenuService;
import com.smartcampus.menu.StockService;
import com.smartcampus.menu.dto.EtaRange;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.Payment;
import com.smartcampus.payment.PaymentService;
import com.smartcampus.user.Role;
import com.smartcampus.user.User;
import com.smartcampus.user.UserService;
import com.smartcampus.vendor.Vendor;
import com.smartcampus.vendor.VendorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The single short placement transaction of rule 5.1 (steps 1 to 5). No gateway call happens in here.
 * Kept separate from OrderServiceImpl so a unique-key violation (two identical requests racing, rule 5.7)
 * rolls back this transaction cleanly and the caller can replay the winning order in a new one.
 */
@Component
class OrderPlacement {

    private final OrderRepository orderRepository;
    private final MenuService menuService;
    private final StockService stockService;
    private final SlotService slotService;
    private final EstimateService estimateService;
    private final PaymentService paymentService;
    private final VendorService vendorService;
    private final UserService userService;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.order.max-unpaid-per-student:2}")
    private int maxUnpaidPerStudent;

    @Value("${app.order.hold-minutes:5}")
    private int holdMinutes;

    @Value("${app.order.max-qty-per-item:10}")
    private int maxQtyPerItem;

    OrderPlacement(OrderRepository orderRepository, MenuService menuService, StockService stockService,
                   SlotService slotService, EstimateService estimateService, @Lazy PaymentService paymentService,
                   VendorService vendorService, UserService userService, OrderMapper orderMapper,
                   ApplicationEventPublisher eventPublisher, Clock clock) {
        this.orderRepository = orderRepository;
        this.menuService = menuService;
        this.stockService = stockService;
        this.slotService = slotService;
        this.estimateService = estimateService;
        this.paymentService = paymentService;
        this.vendorService = vendorService;
        this.userService = userService;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse place(OrderFingerprint cart, long studentId, String idempotencyKey) {
        Instant now = clock.instant();

        // 1. Who is ordering, and are they inside the hoarding limit (5.10)?
        User student = userService.findById(studentId)
                .orElseThrow(() -> new ForbiddenException("Unknown user"));
        if (!student.isActive() || student.getRole() != Role.STUDENT) {
            throw new ForbiddenException("Only active students can place orders");
        }
        orderRepository.lockStudentForPlacement(studentId);
        if (orderRepository.countByStudentIdAndStatus(studentId, OrderStatus.PENDING_PAYMENT) >= maxUnpaidPerStudent) {
            throw new ConflictException("TOO_MANY_UNPAID_ORDERS",
                    "You already have " + maxUnpaidPerStudent + " unpaid orders. Pay or cancel one first.");
        }

        // 1. Shop state
        Vendor vendor = vendorService.getVendorEntity(cart.vendorId());
        if (vendor.isSuspended()) {
            throw new ConflictException("VENDOR_SUSPENDED", vendor.getName() + " is not taking orders");
        }
        if (!vendor.isOpen()) {
            throw new ConflictException("VENDOR_CLOSED", vendor.getName() + " is closed right now");
        }

        // 1. Lines: merged already; every item must exist, belong to this vendor and be switched on
        Map<Long, Integer> lines = cart.mergedItems();
        for (Map.Entry<Long, Integer> line : lines.entrySet()) {
            if (line.getValue() < 1 || line.getValue() > maxQtyPerItem) {
                throw ApiException.badRequest("VALIDATION_ERROR", "items",
                        "Quantity of item " + line.getKey() + " must be between 1 and " + maxQtyPerItem);
            }
        }
        List<MenuItem> items = menuService.getItemsByIdsAscending(lines.keySet());
        if (items.size() != lines.size()) {
            throw ApiException.badRequest("ITEM_UNAVAILABLE", "items", "One or more items do not exist");
        }
        for (MenuItem item : items) {
            if (!item.getVendorId().equals(vendor.getId())) {
                throw ApiException.badRequest("ITEM_UNAVAILABLE", "items",
                        item.getName() + " is not sold by " + vendor.getName());
            }
            if (!item.isAvailable()) {
                throw ApiException.badRequest("ITEM_UNAVAILABLE", "items", item.getName() + " is not available");
            }
        }

        // Fresh estimate from the DB, stored on the order as the promise (Section 6)
        EtaRange promise = estimateService.calculateOrderPromise(vendor, items.stream().map(MenuItem::getPrepMinutes).toList());

        // 2. Pickup slot (optional)
        if (cart.pickupSlotStart() != null) {
            slotService.validateRequestedSlot(vendor, cart.pickupSlotStart(), promise.lowMinutes());
            slotService.reserveSlot(vendor.getId(), cart.pickupSlotStart(), vendor.getSlotMaxOrders());
        }

        // 3. Stock: one conditional UPDATE per item, ascending id; any failure rolls everything back
        for (MenuItem item : items) {
            if (!stockService.tryDecrement(item.getId(), lines.get(item.getId()))) {
                throw new ConflictException("OUT_OF_STOCK", item.getName() + " just sold out");
            }
        }

        // 4. The order, with prices copied from the DB
        Order order = Order.builder()
                .studentId(studentId)
                .vendorId(vendor.getId())
                .status(OrderStatus.PENDING_PAYMENT)
                .idempotencyKey(idempotencyKey)
                .pickupDate(cart.pickupSlotStart() != null ? slotService.dateOf(cart.pickupSlotStart()) : LocalDate.now(clock))
                .pickupSlotStart(cart.pickupSlotStart())
                .createdAt(now)
                .updatedAt(now)
                .holdExpiresAt(now.plus(Duration.ofMinutes(holdMinutes)))
                .estMinutesLow(promise.lowMinutes())
                .estMinutesHigh(promise.highMinutes())
                .estReadyAt(now.plus(Duration.ofMinutes(promise.lowMinutes())))
                .total(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (MenuItem item : items) {
            int qty = lines.get(item.getId());
            orderItems.add(OrderItem.builder()
                    .order(order)
                    .menuItemId(item.getId())
                    .itemName(item.getName())
                    .qty(qty)
                    .unitPrice(item.getPrice())
                    .build());
            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(qty)));
        }
        order.setItems(orderItems);
        order.setTotal(total.setScale(2, RoundingMode.HALF_UP));

        // saveAndFlush so a duplicate (student_id, idempotency_key) fails here, inside this transaction
        order = orderRepository.saveAndFlush(order);
        Payment payment = paymentService.createPayment(order.getId(), order.getTotal());

        // 5. Delivered to SSE/push listeners only after commit
        eventPublisher.publishEvent(new OrderStatusChanged(order.getId(), order.getVendorId(), OrderStatus.PENDING_PAYMENT, now));
        return orderMapper.toResponse(order, payment.getId());
    }
}
