package com.smartcampus.payment;

/**
 * Lifecycle states of a payment.
 * Matches V5__payment_hold_and_eta.sql check constraint.
 */
public enum PaymentStatus {
    INITIATED,
    UNCONFIRMED,
    SUCCESS,
    FAILED,
    REFUNDED,
    REFUND_FAILED
}
