package com.smartcampus.order;

import com.smartcampus.order.events.OrderStatusChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
@RequiredArgsConstructor
@Slf4j
public class UncollectedOrderJob {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.order.uncollected-reminder-minutes:15}")
    private long reminderMinutes;

    @Value("${app.order.uncollected-flag-minutes:45}")
    private long flagMinutes;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void processUncollectedOrders() {
        // Scheduled execution stub
    }
}
