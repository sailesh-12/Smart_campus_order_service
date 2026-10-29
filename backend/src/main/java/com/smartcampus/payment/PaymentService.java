package com.smartcampus.payment;

import com.smartcampus.payment.dto.ManualRefundRequest;
import com.smartcampus.payment.dto.PaymentIssueResponse;
import com.smartcampus.payment.dto.PaymentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service managing payment transactions, charges, refunds, and resolution of disputed payments.
 */
public interface PaymentService {

    /** Gateway idempotency reference for the charge of a payment. */
    static String chargeRef(long paymentId) {
        return "pay-" + paymentId;
    }

    /** Rule 5.12: deterministic refund reference, so a retry can never refund twice. */
    static String refundRef(long paymentId) {
        return "refund-" + paymentId;
    }

    /** Creates the INITIATED payment row inside the order placement transaction (rule 5.1 step 4). */
    Payment createPayment(long orderId, BigDecimal amount);

    /**
     * Business Rule (5.3):
     * Step 2 of payment flow: NOT @Transactional across the gateway call.
     * Invokes PaymentGateway.charge(...) using "pay-{paymentId}" as gateway idempotency reference.
     * - SUCCESS / FAILED go through PaymentOutcomeHandler (rule 5.4), the only place that touches the order.
     * - Timeout, network error or UNKNOWN: payment becomes UNCONFIRMED and nothing else changes.
     * - A payment that is no longer INITIATED is not charged again; its current outcome is returned.
     */
    PaymentOutcome processCharge(long paymentId);

    /**
     * Rule 5.4, first step of the choke point: inside the caller's transaction, moves the payment out of
     * INITIATED/UNCONFIRMED to SUCCESS or FAILED with a conditional update.
     * Returns false when the payment was already final (duplicate outcome: the caller must do nothing).
     */
    boolean recordChargeOutcome(long paymentId, PaymentOutcome outcome);

    /** Payment id per order id, for order lists (one query instead of one per order). */
    java.util.Map<Long, Long> paymentIdsByOrderIds(java.util.Collection<Long> orderIds);

    /**
     * Business Rule (5.6 & 5.12):
     * Refunds a payment using deterministic reference 'refund-{paymentId}'. Runs outside a DB transaction.
     * - Success: moves payment to REFUNDED and publishes PaymentRefunded event.
     * - Failure: moves payment to REFUND_FAILED for admin attention and background reconciliation
     *   (counts as one automatic attempt).
     * - Idempotent: if already REFUNDED, returns true without calling gateway again.
     */
    boolean refund(long paymentId, RefundReason reason);

    /**
     * Schedules {@link #refund} for after the current transaction commits (asynchronously).
     * Used by the order module for cancel, reject and late-success refunds.
     */
    void requestRefundAfterCommit(long paymentId, RefundReason reason);

    /**
     * Rule 5.12: POST /admin/payments/{id}/retry-refund. Only for REFUND_FAILED (otherwise 409 REFUND_NOT_ALLOWED).
     * One more gateway attempt that does not count against the automatic retry cap. Writes an admin_actions row.
     */
    PaymentResponse adminRetryRefund(long paymentId, long adminUserId);

    /**
     * Rule 5.12: POST /admin/payments/{id}/resolve. Marks an issue handled outside the system.
     * Allowed for REFUND_FAILED, and for UNCONFIRMED payments older than app.payment.unconfirmed-max-minutes.
     * Writes an admin_actions row.
     */
    PaymentResponse adminResolve(long paymentId, String note, long adminUserId);

    /**
     * Rule 5.12: POST /admin/payments/{id}/refund. Full refund of a SUCCESS payment with reason COMPLAINT or
     * UNCOLLECTED_OVERRIDE (checked by {@link ManualRefundPolicy}). Stock is not restored.
     * A payment that is already REFUNDED is a no-op returning the current state. Writes an admin_actions row.
     */
    PaymentResponse adminManualRefund(long paymentId, ManualRefundRequest request, long adminUserId);

    Payment getPaymentEntity(long paymentId);

    Payment getPaymentByOrderId(long orderId);

    Optional<Payment> findByOrderId(long orderId);

    PaymentResponse getPayment(long paymentId);

    List<PaymentIssueResponse> getUnresolvedIssues();

    long countUnresolvedIssues();
}
