package com.smartcampus.payment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Section 8 magic amounts of the mock gateway.
 */
class MockPaymentGatewayTest {

    /** A clock the test can move forward. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-23T06:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MovableClock clock;
    private MockPaymentGateway gateway;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        clock = new MovableClock();
        gateway = new MockPaymentGateway(mock(ObjectProvider.class), clock, 30);
    }

    @AfterEach
    void tearDown() {
        gateway.shutdown();
    }

    @Test
    void anyOtherAmount_succeedsImmediately() {
        assertEquals(PaymentOutcome.SUCCESS, gateway.charge("pay-1", new BigDecimal("120.00")));
    }

    @Test
    void paise01_isADefiniteDecline() {
        assertEquals(PaymentOutcome.FAILED, gateway.charge("pay-1", new BigDecimal("120.01")));
    }

    @Test
    void paise02_isUnknownNow_andSuccessAfter20Seconds() {
        assertEquals(PaymentOutcome.UNKNOWN, gateway.charge("pay-2", new BigDecimal("50.02")));
        assertEquals(PaymentOutcome.UNKNOWN, gateway.queryStatus("pay-2", null));
        clock.advance(Duration.ofSeconds(21));
        assertEquals(PaymentOutcome.SUCCESS, gateway.queryStatus("pay-2", null));
    }

    @Test
    void paise03_isUnknownNow_andFailedAfter20Seconds() {
        assertEquals(PaymentOutcome.UNKNOWN, gateway.charge("pay-3", new BigDecimal("50.03")));
        clock.advance(Duration.ofSeconds(21));
        assertEquals(PaymentOutcome.FAILED, gateway.queryStatus("pay-3", null));
    }

    @Test
    void paise04_staysUnknownUntilTheOrderHasExpired() {
        assertEquals(PaymentOutcome.UNKNOWN, gateway.charge("pay-4", new BigDecimal("50.04")));
        clock.advance(Duration.ofMinutes(30));
        assertEquals(PaymentOutcome.UNKNOWN, gateway.queryStatus("pay-4", null));
        clock.advance(Duration.ofMinutes(2));
        assertEquals(PaymentOutcome.SUCCESS, gateway.queryStatus("pay-4", null));
    }

    @Test
    void paise05_chargesButRefundFails() {
        assertEquals(PaymentOutcome.SUCCESS, gateway.charge("pay-5", new BigDecimal("80.05")));
        assertFalse(gateway.refund("refund-5", new BigDecimal("80.05"), null));
        assertTrue(gateway.refund("refund-6", new BigDecimal("80.00"), null));
    }

    @Test
    void sameReference_isNeverChargedTwice() {
        assertEquals(PaymentOutcome.FAILED, gateway.charge("pay-7", new BigDecimal("10.01")));
        // The amount is ignored on a repeat: the first charge's outcome is returned
        assertEquals(PaymentOutcome.FAILED, gateway.charge("pay-7", new BigDecimal("10.00")));
    }

    @Test
    void unknownReference_queriesAsUnknown() {
        assertEquals(PaymentOutcome.UNKNOWN, gateway.queryStatus("pay-404", null));
    }
}
