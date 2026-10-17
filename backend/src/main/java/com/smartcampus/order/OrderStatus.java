package com.smartcampus.order;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * State machine enum representing the lifecycle of an order.
 * Fully implemented per Section 8 specifications.
 */
public enum OrderStatus {
    PENDING_PAYMENT {
        @Override
        public Set<OrderStatus> allowedNext() {
            return EnumSet.of(PAID, CANCELLED, EXPIRED);
        }
    },
    PAID {
        @Override
        public Set<OrderStatus> allowedNext() {
            return EnumSet.of(ACCEPTED, REJECTED, CANCELLED);
        }
    },
    ACCEPTED {
        @Override
        public Set<OrderStatus> allowedNext() {
            return EnumSet.of(PREPARING, REJECTED);
        }
    },
    PREPARING {
        @Override
        public Set<OrderStatus> allowedNext() {
            return EnumSet.of(READY);
        }
    },
    READY {
        @Override
        public Set<OrderStatus> allowedNext() {
            return EnumSet.of(COLLECTED, UNCOLLECTED);
        }
    },
    UNCOLLECTED {
        @Override
        public Set<OrderStatus> allowedNext() {
            return EnumSet.of(COLLECTED);
        }
    },
    COLLECTED {
        @Override
        public Set<OrderStatus> allowedNext() {
            return Collections.emptySet();
        }
    },
    REJECTED {
        @Override
        public Set<OrderStatus> allowedNext() {
            return Collections.emptySet();
        }
    },
    CANCELLED {
        @Override
        public Set<OrderStatus> allowedNext() {
            return Collections.emptySet();
        }
    },
    EXPIRED {
        @Override
        public Set<OrderStatus> allowedNext() {
            return Collections.emptySet();
        }
    };

    /**
     * Returns the set of valid next states from this state.
     */
    public abstract Set<OrderStatus> allowedNext();

    /**
     * Checks if transitioning from this status to nextStatus is permissible.
     */
    public boolean canTransitionTo(OrderStatus nextStatus) {
        return allowedNext().contains(nextStatus);
    }
}
