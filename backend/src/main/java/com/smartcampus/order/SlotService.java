package com.smartcampus.order;

import com.smartcampus.order.dto.SlotResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Service managing pickup slots and capacity reservations.
 */
public interface SlotService {

    /**
     * Business Rule (5.1 & V4):
     * Reserves a slot using a conditional SQL update on slot_capacity.
     * Throws ConflictException(SLOT_FULL) if zero rows are updated.
     * Enforces earliest allowed slot: now + low estimate rounded up to next 5 minutes.
     */
    void reserveSlot(long vendorId, Instant slotStart, int slotMaxOrders);

    void releaseSlot(long vendorId, Instant slotStart);

    List<SlotResponse> getSlotsForVendor(long vendorId, LocalDate date);

    void validateEarliestAllowedSlot(Instant slotStart, int lowEstimateMinutes);
}
