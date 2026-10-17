package com.smartcampus.order;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fully implemented tests for the OrderStatus state machine (Section 8).
 */
class OrderStatusTest {

    @Test
    void pendingPayment_canTransitionTo_paid_cancelled_expired() {
        Set<OrderStatus> allowed = OrderStatus.PENDING_PAYMENT.allowedNext();
        assertEquals(Set.of(OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.EXPIRED), allowed);
    }

    @Test
    void paid_canTransitionTo_accepted_rejected_cancelled() {
        Set<OrderStatus> allowed = OrderStatus.PAID.allowedNext();
        assertEquals(Set.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED), allowed);
    }

    @Test
    void accepted_canTransitionTo_preparing_rejected() {
        Set<OrderStatus> allowed = OrderStatus.ACCEPTED.allowedNext();
        assertEquals(Set.of(OrderStatus.PREPARING, OrderStatus.REJECTED), allowed);
    }

    @Test
    void preparing_canTransitionTo_ready() {
        Set<OrderStatus> allowed = OrderStatus.PREPARING.allowedNext();
        assertEquals(Set.of(OrderStatus.READY), allowed);
    }

    @Test
    void ready_canTransitionTo_collected_uncollected() {
        Set<OrderStatus> allowed = OrderStatus.READY.allowedNext();
        assertEquals(Set.of(OrderStatus.COLLECTED, OrderStatus.UNCOLLECTED), allowed);
    }

    @Test
    void uncollected_canTransitionTo_collected() {
        Set<OrderStatus> allowed = OrderStatus.UNCOLLECTED.allowedNext();
        assertEquals(Set.of(OrderStatus.COLLECTED), allowed);
    }

    @Test
    void terminalStatuses_haveNoNext() {
        for (OrderStatus terminal : new OrderStatus[]{
                OrderStatus.COLLECTED, OrderStatus.REJECTED,
                OrderStatus.CANCELLED, OrderStatus.EXPIRED}) {
            assertTrue(terminal.allowedNext().isEmpty(),
                    terminal + " should have no allowed next states");
        }
    }

    @Test
    void canTransitionTo_validTransition_returnsTrue() {
        assertTrue(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID));
        assertTrue(OrderStatus.PAID.canTransitionTo(OrderStatus.ACCEPTED));
        assertTrue(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.PREPARING));
        assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY));
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.COLLECTED));
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.UNCOLLECTED));
        assertTrue(OrderStatus.UNCOLLECTED.canTransitionTo(OrderStatus.COLLECTED));
    }

    @Test
    void canTransitionTo_invalidTransition_returnsFalse() {
        assertFalse(OrderStatus.COLLECTED.canTransitionTo(OrderStatus.PAID));
        assertFalse(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.READY));
        assertFalse(OrderStatus.PREPARING.canTransitionTo(OrderStatus.PAID));
        assertFalse(OrderStatus.EXPIRED.canTransitionTo(OrderStatus.PAID));
        assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.ACCEPTED));
        assertFalse(OrderStatus.REJECTED.canTransitionTo(OrderStatus.PREPARING));
    }

    @Test
    void canTransitionTo_selfTransition_notAllowed() {
        for (OrderStatus status : OrderStatus.values()) {
            assertFalse(status.canTransitionTo(status),
                    status + " should not transition to itself");
        }
    }

    @Test
    void pendingPayment_cannotSkipToPreparing() {
        assertFalse(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PREPARING));
    }

    @Test
    void paid_cannotGoBackToPendingPayment() {
        assertFalse(OrderStatus.PAID.canTransitionTo(OrderStatus.PENDING_PAYMENT));
    }

    @Test
    void accepted_cannotCancel() {
        assertFalse(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.CANCELLED));
    }
}
