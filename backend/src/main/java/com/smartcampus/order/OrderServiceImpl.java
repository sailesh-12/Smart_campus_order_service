package com.smartcampus.order;

import com.smartcampus.common.exception.ForbiddenException;
import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.common.util.RateLimiter;
import com.smartcampus.menu.MenuService;
import com.smartcampus.menu.StockService;
import com.smartcampus.order.dto.CreateOrderRequest;
import com.smartcampus.order.dto.OrderResponse;
import com.smartcampus.order.dto.PayOrderResponse;
import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.PaymentService;
import com.smartcampus.user.UserService;
import com.smartcampus.vendor.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockService stockService;
    private final SlotService slotService;
    private final EstimateService estimateService;
    private final PickupService pickupService;
    private final PaymentService paymentService;
    private final VendorService vendorService;
    private final UserService userService;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.order.max-unpaid-per-student:2}")
    private int maxUnpaidPerStudent;

    @Value("${app.order.rate-limit-per-minute:5}")
    private int orderRateLimitPerMinute;

    @Value("${app.order.hold-minutes:5}")
    private int holdMinutes;

    @Value("${app.order.max-qty-per-item:10}")
    private int maxQtyPerItem;

    @Override
    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request, long studentId, String idempotencyKey) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public PayOrderResponse payOrder(long orderId, long studentId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public void cancelOrder(long orderId, long studentId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public void updateOrderStatus(long orderId, OrderStatus newStatus, long vendorUserId) {
        throw new UnsupportedOperationException("TODO");
    }

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
    public OrderResponse getOrderById(long orderId, long userId, boolean isVendorOrAdmin) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getStudentOrders(long studentId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getVendorOrders(long vendorId, OrderStatus status) {
        throw new UnsupportedOperationException("TODO");
    }
}
