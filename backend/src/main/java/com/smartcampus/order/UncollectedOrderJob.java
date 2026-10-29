package com.smartcampus.order;

import com.smartcampus.order.events.OrderReminder;
import com.smartcampus.order.events.OrderStatusChanged;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Background job managing abandoned/uncollected orders.
 * Business Rule (5.11):
 * - Runs every minute. Safe across multiple instances via conditional updates.
 * - READY orders older than app.order.uncollected-reminder-minutes (default 15) with reminder_sent_at null:
 *     * Sets reminder_sent_at (once only).
 *     * Triggers push reminder and order-reminder SSE event.
 * - READY orders older than app.order.uncollected-flag-minutes (default 45):
 *     * Conditionally updates status from READY to UNCOLLECTED.
 *     * Sets uncollected_at = now.
 *     * Publishes OrderStatusChanged.
 *     * NO automatic refund and stock is NOT restored (food was already cooked).
 */
@Component
@Slf4j
public class UncollectedOrderJob {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Value("${app.order.uncollected-reminder-minutes:15}")
    private long reminderMinutes;

    @Value("${app.order.uncollected-flag-minutes:45}")
    private long flagMinutes;

    public UncollectedOrderJob(OrderRepository orderRepository,
                               ApplicationEventPublisher eventPublisher,
                               PlatformTransactionManager transactionManager,
                               Clock clock) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 45_000)
    public void processUncollectedOrders() {
        Instant now = clock.instant();

        // Flag first: an order past the flag time does not also need a reminder
        for (Order order : orderRepository.findByStatusAndReadyAtBefore(OrderStatus.READY, now.minus(Duration.ofMinutes(flagMinutes)))) {
            runSafely(order, () -> {
                if (orderRepository.markUncollected(order.getId(), now) == 1) {
                    eventPublisher.publishEvent(new OrderStatusChanged(order.getId(), order.getVendorId(), OrderStatus.UNCOLLECTED, now));
                    log.info("Order {} flagged UNCOLLECTED", order.getId());
                }
            });
        }

        Instant reminderCutoff = now.minus(Duration.ofMinutes(reminderMinutes));
        for (Order order : orderRepository.findByStatusAndReadyAtBeforeAndReminderSentAtIsNull(OrderStatus.READY, reminderCutoff)) {
            runSafely(order, () -> {
                if (orderRepository.markReminderSent(order.getId(), now) == 1) {
                    eventPublisher.publishEvent(new OrderReminder(order.getId(), order.getVendorId(), now));
                }
            });
        }
    }

    private void runSafely(Order order, Runnable work) {
        try {
            tx.executeWithoutResult(s -> work.run());
        } catch (RuntimeException e) {
            log.error("Uncollected-order job failed for order {}: {}", order.getId(), e.getMessage());
        }
    }
}
