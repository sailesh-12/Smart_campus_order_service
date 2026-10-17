package com.smartcampus.order;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for stock hoarding and rate limit protection (Section 5.10).
 */
class HoardingProtectionTest {

    @Test
    @Disabled("TODO: a third unpaid order is rejected with 409 TOO_MANY_UNPAID_ORDERS")
    void thirdUnpaidOrder_rejected409_TOO_MANY_UNPAID() {
    }

    @Test
    @Disabled("TODO: replaying the same idempotency key is not blocked by hoarding check")
    void sameIdempotencyKey_notBlocked() {
    }

    @Test
    @Disabled("TODO: paying or cancelling frees unpaid capacity, allowing a new order")
    void payingOrCancelling_freesCapacity() {
    }

    @Test
    @Disabled("TODO: exceeding rate limit returns 429 with Retry-After header")
    void exceedRateLimit_returns429_withRetryAfter() {
    }
}
