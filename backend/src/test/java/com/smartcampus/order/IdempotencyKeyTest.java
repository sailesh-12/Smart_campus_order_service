package com.smartcampus.order;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for idempotency key handling (Section 5.7).
 */
class IdempotencyKeyTest {

    @Test
    @Disabled("TODO: integration test — same idempotency key with same request returns existing order (200, Idempotent-Replayed: true)")
    void sameKey_samePayload_returnsExistingOrder() {
    }

    @Test
    @Disabled("TODO: integration test — different idempotency keys create distinct orders")
    void differentKeys_createDifferentOrders() {
    }

    @Test
    @Disabled("TODO: integration test — same key with different payload returns 409 IDEMPOTENCY_KEY_REUSED")
    void sameKey_differentPayload_returns409() {
    }

    @Test
    @Disabled("TODO: integration test — two identical requests racing catch unique-violation and return existing order")
    void twoIdenticalRequests_racing_oneWinsOtherReturnsExisting() {
    }
}
