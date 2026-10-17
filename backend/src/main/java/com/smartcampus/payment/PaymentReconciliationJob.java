package com.smartcampus.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Background job reconciling in-flight payments and retrying failed refunds.
 * Business Rule (5.5b):
 * - Runs every app.payment.reconcile-interval-seconds.
 * - Scans payments in UNCONFIRMED, and INITIATED older than 2 minutes with provider_ref.
 * - Calls PaymentGateway.queryStatus:
 *     * Definite SUCCESS or FAILED delegates to PaymentOutcomeHandler (Section 5.4).
 *     * UNKNOWN increments check_count and last_checked_at and waits.
 * - Retries REFUND_FAILED payments up to app.payment.refund-max-attempts times.
 * - Safe to run concurrently on multiple instances (idempotent, guarded by conditional DB updates).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentOutcomeHandler outcomeHandler;
    private final PaymentService paymentService;
    private final Clock clock;

    @Value("${app.payment.refund-max-attempts:3}")
    private int maxRefundAttempts;

    @Scheduled(fixedDelayString = "${app.payment.reconcile-interval-seconds:30}000")
    public void reconcile() {
        // Scheduled reconciliation execution stub
    }
}
