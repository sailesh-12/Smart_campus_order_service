package com.smartcampus.payment;

/**
 * Checks that need other modules before an admin manual refund (rule 5.12).
 * The payment module may not import order or complaint, so it defines this interface and the
 * complaint module (which may depend on order and payment) implements it.
 */
public interface ManualRefundPolicy {

    /**
     * Throws if the refund is not allowed:
     * - UNCOLLECTED_OVERRIDE: the order must be UNCOLLECTED.
     * - a given complaintId must belong to the same order.
     */
    void validate(long orderId, RefundReason reason, Long complaintId);

    /**
     * Called inside the transaction that records a successful manual refund:
     * links the complaint (refund_payment_id) and moves it to RESOLVED.
     */
    void afterRefund(long paymentId, long orderId, Long complaintId);
}
