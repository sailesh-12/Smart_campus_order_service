package com.smartcampus.order;

import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.Payment;
import com.smartcampus.payment.PaymentService;
import com.smartcampus.payment.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Background job scanning unpaid orders past hold deadline.
 * Business Rule (5.5b):
 * - Runs every minute. Safe across multiple instances via conditional update guards.
 * - Scans PENDING_PAYMENT orders past hold_expires_at.
 * - If payment status is UNCONFIRMED:
 *     * Skip order (letting reconciliation resolve it) until older than app.payment.unconfirmed-max-minutes.
 *     * After timeout cap, expire order and restore stock/slot once, but leave payment UNCONFIRMED so late success auto-refunds.
 * - If payment is INITIATED (no charge in flight):
 *     * Expire order (EXPIRED).
 *     * Restore stock and booked pickup slot once.
 * Each order is expired in its own short transaction, so one failure does not undo the others.
 */
@Component
@Slf4j
public class OrderExpiryJob {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final OrderInventoryReleaser inventoryReleaser;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Value("${app.payment.unconfirmed-max-minutes:30}")
    private long unconfirmedMaxMinutes;

    public OrderExpiryJob(OrderRepository orderRepository,
                          @Lazy PaymentService paymentService,
                          OrderInventoryReleaser inventoryReleaser,
                          ApplicationEventPublisher eventPublisher,
                          PlatformTransactionManager transactionManager,
                          Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.inventoryReleaser = inventoryReleaser;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 30_000)
    public void expireUnpaidOrders() {
        Instant now = clock.instant();
        for (Order order : orderRepository.findByStatusAndHoldExpiresAtBefore(OrderStatus.PENDING_PAYMENT, now)) {
            try {
                expire(order, now);
            } catch (RuntimeException e) {
                log.error("Could not expire order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    private void expire(Order order, Instant now) {
        PaymentStatus paymentStatus = paymentService.findByOrderId(order.getId())
                .map(Payment::getStatus)
                .orElse(PaymentStatus.INITIATED);
        if (paymentStatus == PaymentStatus.UNCONFIRMED
                && Duration.between(order.getCreatedAt(), now).toMinutes() < unconfirmedMaxMinutes) {
            return; // a charge may be in flight; reconciliation decides
        }

        tx.executeWithoutResult(s -> {
            int won = orderRepository.updateStatusConditional(order.getId(), OrderStatus.EXPIRED, OrderStatus.PENDING_PAYMENT, now);
            if (won == 1) {
                inventoryReleaser.release(order.getId(), order.getVendorId(), order.getPickupSlotStart());
                eventPublisher.publishEvent(new OrderStatusChanged(order.getId(), order.getVendorId(), OrderStatus.EXPIRED, now));
                log.info("Expired unpaid order {} (payment {})", order.getId(), paymentStatus);
            }
        });
    }
}
