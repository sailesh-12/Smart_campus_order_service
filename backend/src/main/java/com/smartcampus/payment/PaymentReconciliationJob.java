package com.smartcampus.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
 * Each payment is handled on its own, so one failure never blocks the rest of the batch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationJob {

    private static final Duration INITIATED_GRACE = Duration.ofMinutes(2);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentOutcomeHandler outcomeHandler;
    private final PaymentService paymentService;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Value("${app.payment.refund-max-attempts:3}")
    private int maxRefundAttempts;

    @Scheduled(fixedDelayString = "${app.payment.reconcile-interval-seconds:30}000",
            initialDelayString = "${app.payment.reconcile-interval-seconds:30}000")
    public void reconcile() {
        Instant now = clock.instant();
        List<Payment> candidates = paymentRepository.findPaymentsForReconciliation(now.minus(INITIATED_GRACE), maxRefundAttempts);
        for (Payment payment : candidates) {
            try {
                switch (payment.getStatus()) {
                    case UNCONFIRMED, INITIATED -> reconcileCharge(payment);
                    case REFUND_FAILED -> paymentService.refund(payment.getId(), payment.getRefundReason());
                    default -> { /* settled meanwhile by another pod or the webhook */ }
                }
            } catch (RuntimeException e) {
                log.error("Reconciliation of payment {} failed: {}", payment.getId(), e.getMessage());
            }
        }
    }

    private void reconcileCharge(Payment payment) {
        PaymentOutcome outcome;
        try {
            outcome = paymentGateway.queryStatus(PaymentService.chargeRef(payment.getId()), payment.getProviderRef());
        } catch (RuntimeException e) {
            outcome = PaymentOutcome.UNKNOWN;
        }
        if (outcome == PaymentOutcome.SUCCESS || outcome == PaymentOutcome.FAILED) {
            log.info("Reconciliation settled payment {} as {}", payment.getId(), outcome);
            outcomeHandler.onDefiniteOutcome(payment.getId(), outcome);
        } else {
            Instant now = clock.instant();
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(s -> paymentRepository.recordStatusCheck(payment.getId(), now));
        }
    }
}
