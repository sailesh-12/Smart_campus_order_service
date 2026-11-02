package com.smartcampus.payment;

import com.smartcampus.payment.events.RefundRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs requested refunds after the order change that caused them has committed, so the gateway call
 * never happens inside a database transaction and a rolled-back cancel never refunds.
 * A failure here is not lost: the payment ends in REFUND_FAILED and the reconciliation job retries it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundRequestListener {

    private final PaymentService paymentService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRefundRequested(RefundRequested event) {
        try {
            paymentService.refund(event.paymentId(), event.reason());
        } catch (RuntimeException e) {
            log.error("Refund of payment {} ({}) could not be attempted: {}", event.paymentId(), event.reason(), e.getMessage());
        }
    }
}
