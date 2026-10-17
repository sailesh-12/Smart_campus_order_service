package com.smartcampus.payment;

import java.math.BigDecimal;

/**
 * Interface representing external payment provider gateway.
 */
public interface PaymentGateway {

    /**
     * Attempts to charge the payment.
     * Uses paymentId or unique order payment ref as gateway idempotency reference.
     */
    PaymentOutcome charge(String idempotencyRef, BigDecimal amount);

    /**
     * Queries gateway status for UNCONFIRMED or INITIATED transactions during reconciliation.
     */
    PaymentOutcome queryStatus(String idempotencyRef, String providerRef);

    /**
     * Attempts a refund with a deterministic idempotency key (refund-{paymentId}).
     * Returns true on refund success, false on failure.
     */
    boolean refund(String idempotencyRef, BigDecimal amount, String providerRef);
}
