package com.smartcampus.payment;

import com.smartcampus.payment.dto.PaymentIssueResponse;
import com.smartcampus.payment.dto.PaymentResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service managing payment transactions, charges, refunds, and resolution of disputed payments.
 */
public interface PaymentService {

    Payment createPayment(long orderId, BigDecimal amount);

    /**
     * Business Rule (5.3):
     * Step 2 of payment flow: NOT @Transactional across the gateway call.
     * Invokes PaymentGateway.charge(...) using payment id as gateway idempotency reference.
     * Updates payment status to SUCCESS, FAILED, or UNCONFIRMED accordingly.
     */
    PaymentOutcome processCharge(long paymentId, BigDecimal amount);

    /**
     * Business Rule (5.6 & 5.12):
     * Refunds a payment using deterministic reference 'refund-{paymentId}'.
     * - Success: moves payment to REFUNDED and publishes PaymentRefunded event.
     * - Failure: moves payment to REFUND_FAILED for admin attention and background reconciliation.
     * - Idempotent: if already REFUNDED, returns successfully without calling gateway again.
     */
    boolean refund(long paymentId, RefundReason reason);

    /**
     * Resolves an unresolved payment issue manually.
     */
    void resolvePayment(long paymentId, String resolutionNote, long adminUserId);

    Payment getPaymentEntity(long paymentId);

    Payment getPaymentByOrderId(long orderId);

    List<PaymentIssueResponse> getUnresolvedIssues();
}
