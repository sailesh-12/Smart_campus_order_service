package com.smartcampus.payment;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * - .02 -> UNKNOWN now; after 20s sends SUCCESS through the webhook processing path
 * - .03 -> UNKNOWN now; after 20s sends FAILED through the webhook processing path
 * - .04 -> UNKNOWN now; SUCCESS only after the order has expired (late success, auto-refunded).
 *          The expiry job keeps UNCONFIRMED orders until app.payment.unconfirmed-max-minutes, so the
 *          success arrives that long after the charge plus 90 seconds.
 * - .05 -> SUCCESS on charge, but every refund call fails (REFUND_FAILED)
 *
 * Like a real gateway it is idempotent per reference: charging the same "pay-{id}" twice never
 * creates a second charge, and queryStatus (used by reconciliation) reports UNKNOWN until the
 * delayed outcome is due, so reconciliation cannot settle a payment earlier than the scenario intends.
 * State is in memory: after a restart, unfinished mock payments stay UNKNOWN (an admin can resolve them).
 */
@Component
@ConditionalOnProperty(name = "app.payment.mock.magic-amounts", havingValue = "true")
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    private record MockCharge(PaymentOutcome outcome, Instant settledAt) {
    }

    private final ObjectProvider<PaymentWebhookService> webhookServiceProvider;
    private final Clock clock;
    private final long lateSuccessDelaySeconds;
    private final Map<String, MockCharge> charges = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mock-gateway");
        t.setDaemon(true);
        return t;
    });

    public MockPaymentGateway(ObjectProvider<PaymentWebhookService> webhookServiceProvider,
                              Clock clock,
                              @Value("${app.payment.unconfirmed-max-minutes:30}") long unconfirmedMaxMinutes) {
        this.webhookServiceProvider = webhookServiceProvider;
        this.clock = clock;
        this.lateSuccessDelaySeconds = unconfirmedMaxMinutes * 60 + 90;
    }

    @Override
    public PaymentOutcome charge(String idempotencyRef, BigDecimal amount) {
        MockCharge existing = charges.get(idempotencyRef);
        if (existing != null) {
            log.info("[MOCK GATEWAY] Repeat charge for ref={} (no second charge)", idempotencyRef);
            return visibleOutcome(existing);
        }

        int paise = extractPaise(amount);
        log.info("[MOCK GATEWAY] Charge request: ref={}, amount={}, paise={}", idempotencyRef, amount, paise);
        Instant now = clock.instant();

        MockCharge charge = switch (paise) {
            case 1 -> new MockCharge(PaymentOutcome.FAILED, now);
            case 2 -> scheduleDelayedOutcome(idempotencyRef, PaymentOutcome.SUCCESS, 20);
            case 3 -> scheduleDelayedOutcome(idempotencyRef, PaymentOutcome.FAILED, 20);
            case 4 -> scheduleDelayedOutcome(idempotencyRef, PaymentOutcome.SUCCESS, lateSuccessDelaySeconds);
            default -> new MockCharge(PaymentOutcome.SUCCESS, now);
        };
        charges.put(idempotencyRef, charge);
        PaymentOutcome visible = visibleOutcome(charge);
        log.info("[MOCK GATEWAY] ref={} -> {}", idempotencyRef, visible);
        return visible;
    }

    @Override
    public PaymentOutcome queryStatus(String idempotencyRef, String providerRef) {
        MockCharge charge = charges.get(idempotencyRef);
        PaymentOutcome outcome = charge == null ? PaymentOutcome.UNKNOWN : visibleOutcome(charge);
        log.info("[MOCK GATEWAY] Query ref={} -> {}", idempotencyRef, outcome);
        return outcome;
    }

    @Override
    public boolean refund(String idempotencyRef, BigDecimal amount, String providerRef) {
        int paise = extractPaise(amount);
        log.info("[MOCK GATEWAY] Refund request: ref={}, amount={}, paise={}", idempotencyRef, amount, paise);
        if (paise == 5) {
            log.warn("[MOCK GATEWAY] Magic amount .05: refund failed");
            return false;
        }
        return true;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    private PaymentOutcome visibleOutcome(MockCharge charge) {
        return clock.instant().isBefore(charge.settledAt()) ? PaymentOutcome.UNKNOWN : charge.outcome();
    }

    private static int extractPaise(BigDecimal amount) {
        if (amount == null) {
            return 0;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).remainder(BigDecimal.valueOf(100)).intValue();
    }

    private MockCharge scheduleDelayedOutcome(String paymentRef, PaymentOutcome outcome, long delaySeconds) {
        log.info("[MOCK GATEWAY] ref={} UNKNOWN now, {} in {}s", paymentRef, outcome, delaySeconds);
        scheduler.schedule(() -> {
            try {
                long paymentId = parsePaymentId(paymentRef);
                PaymentWebhookService webhookService = webhookServiceProvider.getIfAvailable();
                if (webhookService != null) {
                    log.info("[MOCK GATEWAY] Delivering delayed {} for paymentId={} via webhook path", outcome, paymentId);
                    webhookService.process("mock-" + paymentRef + "-" + outcome, paymentId, outcome.name(), null);
                }
            } catch (Exception e) {
                log.error("[MOCK GATEWAY] Failed to deliver delayed outcome for {}: {}", paymentRef, e.getMessage());
            }
        }, delaySeconds, TimeUnit.SECONDS);
        return new MockCharge(outcome, clock.instant().plusSeconds(delaySeconds));
    }

    private static long parsePaymentId(String paymentRef) {
        String digits = paymentRef.startsWith("pay-") ? paymentRef.substring(4) : paymentRef;
        return Long.parseLong(digits);
    }
}
