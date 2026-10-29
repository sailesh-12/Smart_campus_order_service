package com.smartcampus.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Placeholder used when the mock gateway is off and no real gateway integration exists yet
 * (the real provider is out of scope for this phase). It never moves money:
 * charges stay UNKNOWN (payment UNCONFIRMED, stock held, nothing lost) and refunds fail into REFUND_FAILED,
 * so every payment shows up in the admin issues list instead of silently succeeding.
 * Replace this bean with the real provider's PaymentGateway implementation.
 */
@Component
@ConditionalOnProperty(name = "app.payment.mock.magic-amounts", havingValue = "false", matchIfMissing = true)
@Slf4j
public class UnconfiguredPaymentGateway implements PaymentGateway {

    @Override
    public PaymentOutcome charge(String idempotencyRef, BigDecimal amount) {
        log.error("No payment gateway configured; charge {} left UNCONFIRMED", idempotencyRef);
        return PaymentOutcome.UNKNOWN;
    }

    @Override
    public PaymentOutcome queryStatus(String idempotencyRef, String providerRef) {
        return PaymentOutcome.UNKNOWN;
    }

    @Override
    public boolean refund(String idempotencyRef, BigDecimal amount, String providerRef) {
        log.error("No payment gateway configured; refund {} not possible", idempotencyRef);
        return false;
    }
}
