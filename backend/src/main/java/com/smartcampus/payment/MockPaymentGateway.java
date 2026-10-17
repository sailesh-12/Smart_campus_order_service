package com.smartcampus.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mock payment gateway for local development and integration testing.
 * Enabled via app.payment.mock.magic-amounts=true.
 *
 * Implements deterministic behaviour based on the paise (cents) of the order total:
 * - Any other value -> immediate SUCCESS
 * - .01 -> definite DECLINE (FAILED)
 * - .02 -> UNKNOWN now; after 20s sends SUCCESS via webhook/outcome handler
 * - .03 -> UNKNOWN now; after 20s sends FAILED via webhook/outcome handler
 * - .04 -> UNKNOWN now; SUCCESS delivered after hold expires (triggers late success auto-refund)
 * - .05 -> SUCCESS on charge, but refund call fails (REFUND_FAILED)
 */
@Component
@ConditionalOnProperty(name = "app.payment.mock.magic-amounts", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    private final ObjectProvider<PaymentOutcomeHandler> outcomeHandlerProvider;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public PaymentOutcome charge(String idempotencyRef, BigDecimal amount) {
        int paise = extractPaise(amount);
        log.info("[MOCK GATEWAY] Charge request: ref={}, amount={}, paise={}", idempotencyRef, amount, paise);

        return switch (paise) {
            case 1 -> {
                log.info("[MOCK GATEWAY] Magic amount .01: Definite DECLINE");
                yield PaymentOutcome.FAILED;
            }
            case 2 -> {
                log.info("[MOCK GATEWAY] Magic amount .02: UNKNOWN now, async SUCCESS in 20s");
                scheduleDelayedOutcome(idempotencyRef, PaymentOutcome.SUCCESS, 20);
                yield PaymentOutcome.UNKNOWN;
            }
            case 3 -> {
                log.info("[MOCK GATEWAY] Magic amount .03: UNKNOWN now, async FAILED in 20s");
                scheduleDelayedOutcome(idempotencyRef, PaymentOutcome.FAILED, 20);
                yield PaymentOutcome.UNKNOWN;
            }
            case 4 -> {
                log.info("[MOCK GATEWAY] Magic amount .04: UNKNOWN now, late SUCCESS in 310s (after hold expires)");
                scheduleDelayedOutcome(idempotencyRef, PaymentOutcome.SUCCESS, 310);
                yield PaymentOutcome.UNKNOWN;
            }
            default -> {
                log.info("[MOCK GATEWAY] Amount {}: Immediate SUCCESS", amount);
                yield PaymentOutcome.SUCCESS;
            }
        };
    }

    @Override
    public PaymentOutcome queryStatus(String idempotencyRef, String providerRef) {
        log.info("[MOCK GATEWAY] Querying status for ref={}", idempotencyRef);
        return PaymentOutcome.SUCCESS;
    }

    @Override
    public boolean refund(String idempotencyRef, BigDecimal amount, String providerRef) {
        int paise = extractPaise(amount);
        log.info("[MOCK GATEWAY] Refund request: ref={}, amount={}, paise={}", idempotencyRef, amount, paise);

        if (paise == 5) {
            log.warn("[MOCK GATEWAY] Magic amount .05: Refund failed");
            return false;
        }
        log.info("[MOCK GATEWAY] Refund succeeded for ref={}", idempotencyRef);
        return true;
    }

    private int extractPaise(BigDecimal amount) {
        if (amount == null) return 0;
        return amount.multiply(BigDecimal.valueOf(100)).remainder(BigDecimal.valueOf(100)).intValue();
    }

    private void scheduleDelayedOutcome(String paymentRef, PaymentOutcome outcome, int delaySeconds) {
        scheduler.schedule(() -> {
            try {
                long paymentId = parsePaymentId(paymentRef);
                PaymentOutcomeHandler handler = outcomeHandlerProvider.getIfAvailable();
                if (handler != null) {
                    log.info("[MOCK GATEWAY] Firing delayed webhook outcome {} for paymentId={}", outcome, paymentId);
                    handler.onDefiniteOutcome(paymentId, outcome);
                }
            } catch (Exception e) {
                log.error("[MOCK GATEWAY] Failed to dispatch delayed outcome: {}", e.getMessage());
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private long parsePaymentId(String paymentRef) {
        try {
            if (paymentRef.startsWith("pay-")) {
                return Long.parseLong(paymentRef.substring(4));
            }
            return Long.parseLong(paymentRef);
        } catch (NumberFormatException e) {
            return 1L;
        }
    }
}
