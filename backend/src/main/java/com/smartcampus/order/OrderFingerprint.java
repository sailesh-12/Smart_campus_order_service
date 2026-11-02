package com.smartcampus.order;

import com.smartcampus.order.dto.CreateOrderRequest;
import com.smartcampus.order.dto.OrderItemRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Rule 5.7: what "the same request" means for an Idempotency-Key replay.
 * Vendor, the merged cart (duplicate lines of the same item are summed, rule 5.1) and the pickup slot.
 * Line order and how the cart was split into lines do not matter; prices never come from the client.
 */
record OrderFingerprint(long vendorId, SortedMap<Long, Integer> items, Instant pickupSlotStart) {

    OrderFingerprint {
        items = Collections.unmodifiableSortedMap(new TreeMap<>(items));
        pickupSlotStart = pickupSlotStart == null ? null : pickupSlotStart.truncatedTo(ChronoUnit.SECONDS);
    }

    static OrderFingerprint of(CreateOrderRequest request) {
        SortedMap<Long, Integer> merged = new TreeMap<>();
        for (OrderItemRequest line : request.items()) {
            merged.merge(line.menuItemId(), line.qty(), Integer::sum);
        }
        return new OrderFingerprint(request.vendorId(), merged, request.pickupSlotStart());
    }

    static OrderFingerprint of(Order order) {
        SortedMap<Long, Integer> merged = new TreeMap<>();
        for (OrderItem line : order.getItems()) {
            merged.merge(line.getMenuItemId(), line.getQty(), Integer::sum);
        }
        return new OrderFingerprint(order.getVendorId(), merged, order.getPickupSlotStart());
    }

    boolean sameRequestAs(OrderFingerprint other) {
        return other != null
                && vendorId == other.vendorId
                && items.equals(other.items)
                && Objects.equals(pickupSlotStart, other.pickupSlotStart);
    }

    Map<Long, Integer> mergedItems() {
        return items;
    }
}
