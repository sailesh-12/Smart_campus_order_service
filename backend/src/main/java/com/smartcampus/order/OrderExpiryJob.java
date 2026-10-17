package com.smartcampus.order;

import com.smartcampus.menu.StockService;
import com.smartcampus.order.events.OrderStatusChanged;
import com.smartcampus.payment.Payment;
import com.smartcampus.payment.PaymentRepository;
import com.smartcampus.payment.PaymentStatus;
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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryJob {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final StockService stockService;
    private final SlotService slotService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.payment.unconfirmed-max-minutes:30}")
    private long unconfirmedMaxMinutes;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireUnpaidOrders() {
        // Scheduled execution stub
    }
}
