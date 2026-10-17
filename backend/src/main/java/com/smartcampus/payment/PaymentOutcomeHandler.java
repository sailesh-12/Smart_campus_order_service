package com.smartcampus.payment;

/**
 * Interface implemented by order.OrderPaymentResolver.
 * Serves as the single choke point for applying definite payment outcomes to orders.
 */
public interface PaymentOutcomeHandler {

    /**
     * Applies a definite payment outcome (SUCCESS or FAILED) to an order.
     * Business Rule (5.4):
     * - Executed inside a single transaction.
     * - Conditionally moves the payment out of INITIATED/UNCONFIRMED.
     * - If payment is already final, does nothing (idempotent no-op).
     * - SUCCESS and order PENDING_PAYMENT: conditional update to PAID, generate pickup code & QR secret, publish events.
     * - SUCCESS but order already EXPIRED or CANCELLED (late success): record SUCCESS and immediately auto-refund, notify student. Do not revive order.
     * - FAILED and order PENDING_PAYMENT: conditional update to CANCELLED, restore stock and slot once.
     *
     * @param paymentId payment identifier
     * @param outcome definite outcome (SUCCESS or FAILED)
     */
    void onDefiniteOutcome(long paymentId, PaymentOutcome outcome);
}
