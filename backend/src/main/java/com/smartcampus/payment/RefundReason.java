package com.smartcampus.payment;

/**
 * Valid reasons for executing a payment refund.
 * Matches V6__push_uncollected_admin_money_reset.sql constraint.
 */
public enum RefundReason {
    CANCELLED,
    REJECTED,
    LATE_SUCCESS,
    COMPLAINT,
    UNCOLLECTED_OVERRIDE
}
