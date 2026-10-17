package com.smartcampus.order;

import com.smartcampus.menu.StockService;
import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.Payment;
import com.smartcampus.payment.PaymentOutcome;
import com.smartcampus.payment.PaymentOutcomeHandler;
import com.smartcampus.payment.PaymentRepository;
import com.smartcampus.payment.PaymentService;
import com.smartcampus.payment.PaymentStatus;
import com.smartcampus.payment.RefundReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/**
 * Single choke point implementation for processing definite payment outcomes onto orders.
 * Implements payment.PaymentOutcomeHandler.
 *
 * Business Rules (Section 5.3, 5.4, 5.6):
 * - Executed in a single database transaction.
 * - Conditionally moves the payment out of INITIATED/UNCONFIRMED. Duplicate outcomes are no-ops.
 * - If outcome is SUCCESS and order is PENDING_PAYMENT:
 *     * Conditionally updates order status to PAID.
 *     * Generates pickup code (A47 style) and random QR secret.
 *     * Publishes OrderStatusChanged event.
 * - If outcome is SUCCESS but order is already EXPIRED or CANCELLED (late success):
 *     * Records payment as SUCCESS.
 *     * Immediately executes automatic refund (RefundReason.LATE_SUCCESS).
 *     * Does NOT revive the order.
 * - If outcome is FAILED and order is PENDING_PAYMENT:
 *     * Conditionally updates order status to CANCELLED.
 *     * Restores reserved item stock and slot capacity EXACTLY ONCE.
 */
@Service
@Slf4j
public class OrderPaymentResolver implements PaymentOutcomeHandler {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final StockService stockService;
    private final SlotService slotService;
    private final PickupService pickupService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public OrderPaymentResolver(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            @Lazy PaymentService paymentService,
            StockService stockService,
            SlotService slotService,
            PickupService pickupService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.stockService = stockService;
        this.slotService = slotService;
        this.pickupService = pickupService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void onDefiniteOutcome(long paymentId, PaymentOutcome outcome) {
        Instant now = clock.instant();
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.warn("Payment {} not found during outcome resolution", paymentId);
            return;
        }

        PaymentStatus targetPaymentStatus = (outcome == PaymentOutcome.SUCCESS) ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        // Conditional update to guard against duplicate webhook/pay races
        int updatedPayment = paymentRepository.updateStatusConditional(
                paymentId, targetPaymentStatus, Set.of(PaymentStatus.INITIATED, PaymentStatus.UNCONFIRMED), now);

        if (updatedPayment == 0 && payment.getStatus() != targetPaymentStatus) {
            log.info("Payment {} already finalized to status {}", paymentId, payment.getStatus());
            return;
        }

        Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
        if (order == null) {
            log.error("Order {} missing for payment {}", payment.getOrderId(), paymentId);
            return;
        }

        if (outcome == PaymentOutcome.SUCCESS) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                int updatedOrder = orderRepository.updateStatusConditional(order.getId(), OrderStatus.PAID, OrderStatus.PENDING_PAYMENT, now);
                if (updatedOrder == 1) {
                    pickupService.generatePickupCredentials(order);
                    eventPublisher.publishEvent(new OrderStatusChanged(order.getId(), order.getVendorId(), OrderStatus.PAID, now));
                }
            } else if (order.getStatus() == OrderStatus.EXPIRED || order.getStatus() == OrderStatus.CANCELLED) {
                log.warn("Late payment success for order {} in status {}. Triggering auto-refund.", order.getId(), order.getStatus());
                paymentService.refund(paymentId, RefundReason.LATE_SUCCESS);
            }
        } else if (outcome == PaymentOutcome.FAILED) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                int updatedOrder = orderRepository.updateStatusConditional(order.getId(), OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, now);
                if (updatedOrder == 1) {
                    // Restore stock and slot once
                    for (OrderItem item : order.getItems()) {
                        stockService.restore(item.getMenuItemId(), item.getQty());
                    }
                    if (order.getPickupSlotStart() != null) {
                        slotService.releaseSlot(order.getVendorId(), order.getPickupSlotStart());
                    }
                    eventPublisher.publishEvent(new OrderStatusChanged(order.getId(), order.getVendorId(), OrderStatus.CANCELLED, now));
                }
            }
        }
    }
}
