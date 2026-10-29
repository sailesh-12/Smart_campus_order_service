package com.smartcampus.order;

import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.Payment;
import com.smartcampus.payment.PaymentOutcome;
import com.smartcampus.payment.PaymentOutcomeHandler;
import com.smartcampus.payment.PaymentService;
import com.smartcampus.payment.RefundReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Single choke point implementation for processing definite payment outcomes onto orders.
 * Implements payment.PaymentOutcomeHandler. Called by /pay, the webhook and the reconciliation job.
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
 *     * Automatically refunds (RefundReason.LATE_SUCCESS) right after commit; the student is told
 *       through the PaymentRefunded event (SSE + push).
 *     * Does NOT revive the order.
 * - If outcome is FAILED and order is PENDING_PAYMENT:
 *     * Conditionally updates order status to CANCELLED.
 *     * Restores reserved item stock and slot capacity EXACTLY ONCE.
 */
@Service
@Slf4j
public class OrderPaymentResolver implements PaymentOutcomeHandler {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final OrderInventoryReleaser inventoryReleaser;
    private final PickupService pickupService;
    private final SlotService slotService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public OrderPaymentResolver(
            OrderRepository orderRepository,
            @Lazy PaymentService paymentService,
            OrderInventoryReleaser inventoryReleaser,
            PickupService pickupService,
            SlotService slotService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.inventoryReleaser = inventoryReleaser;
        this.pickupService = pickupService;
        this.slotService = slotService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void onDefiniteOutcome(long paymentId, PaymentOutcome outcome) {
        if (outcome != PaymentOutcome.SUCCESS && outcome != PaymentOutcome.FAILED) {
            log.warn("Ignoring non-definite outcome {} for payment {}", outcome, paymentId);
            return;
        }
        Payment payment = paymentService.getPaymentEntity(paymentId);
        long orderId = payment.getOrderId();

        if (!paymentService.recordChargeOutcome(paymentId, outcome)) {
            log.info("Payment {} already final; duplicate {} outcome ignored", paymentId, outcome);
            return;
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.error("Order {} missing for payment {}", orderId, paymentId);
            return;
        }
        Instant now = clock.instant();

        if (outcome == PaymentOutcome.SUCCESS) {
            applySuccess(order, paymentId, now);
        } else {
            applyFailure(order, paymentId, now);
        }
    }

    private void applySuccess(Order order, long paymentId, Instant now) {
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                && orderRepository.updateStatusConditional(order.getId(), OrderStatus.PAID, OrderStatus.PENDING_PAYMENT, now) == 1) {
            LocalDate pickupDate = order.getPickupSlotStart() != null
                    ? slotService.dateOf(order.getPickupSlotStart())
                    : LocalDate.now(clock);
            pickupService.generatePickupCredentials(order.getId(), order.getVendorId(), pickupDate);
            eventPublisher.publishEvent(new OrderStatusChanged(order.getId(), order.getVendorId(), OrderStatus.PAID, now));
            return;
        }

        // Either the order was already closed, or it was expired/cancelled in a race just now: re-read it
        OrderStatus current = orderRepository.findById(order.getId()).map(Order::getStatus).orElse(order.getStatus());
        if (current == OrderStatus.EXPIRED || current == OrderStatus.CANCELLED) {
            log.warn("Late payment success for order {} in status {}. Refunding automatically.", order.getId(), current);
            paymentService.requestRefundAfterCommit(paymentId, RefundReason.LATE_SUCCESS);
        } else {
            log.info("Payment {} succeeded for order {} already in {}", paymentId, order.getId(), current);
        }
    }

    private void applyFailure(Order order, long paymentId, Instant now) {
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                && orderRepository.updateStatusConditional(order.getId(), OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, now) == 1) {
            inventoryReleaser.release(order.getId(), order.getVendorId(), order.getPickupSlotStart());
            eventPublisher.publishEvent(new OrderStatusChanged(order.getId(), order.getVendorId(), OrderStatus.CANCELLED, now));
        } else {
            log.info("Payment {} failed; order {} was already {} (nothing to restore)", paymentId, order.getId(), order.getStatus());
        }
    }
}
