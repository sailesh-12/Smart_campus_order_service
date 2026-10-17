package com.smartcampus.order;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for student cancellation edge cases (Section 5.6).
 */
class CancelTest {

    @Test
    @Disabled("TODO: student cannot cancel while payment is UNCONFIRMED — returns 409 PAYMENT_UNCONFIRMED")
    void studentCannotCancel_whilePaymentUnconfirmed_returns409() {
    }
}
