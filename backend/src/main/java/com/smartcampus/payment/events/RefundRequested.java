package com.smartcampus.payment.events;

import com.smartcampus.payment.RefundReason;

/**
 * Internal event: a refund must be made once the current transaction commits.
 * Lets the order module cancel/reject/expire inside its own short transaction while the
 * gateway refund call happens outside any database transaction (rule 5.3 / 5.6).
 */
public record RefundRequested(long paymentId, RefundReason reason) {
}
