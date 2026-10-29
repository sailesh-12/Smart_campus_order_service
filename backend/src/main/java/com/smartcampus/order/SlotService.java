package com.smartcampus.order;

import com.smartcampus.order.dto.SlotResponse;
import com.smartcampus.vendor.Vendor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Service managing pickup slots and capacity reservations.
 * Slots are 5 minutes long, inside the daily window app.slots.open-time .. app.slots.close-time (campus time zone),
 * at most app.slots.max-days-ahead days ahead.
 */
public interface SlotService {

    int SLOT_MINUTES = 5;

    /**
     * Business Rule (5.1 & V4):
     * Reserves a slot using a conditional SQL update on slot_capacity.
     * Throws ConflictException(SLOT_FULL) if zero rows are updated.
     */
    void reserveSlot(long vendorId, Instant slotStart, int slotMaxOrders);

    void releaseSlot(long vendorId, Instant slotStart);

    /** GET /vendors/{id}/slots?date= : bookable slots from the earliest allowed one to closing time. */
    List<SlotResponse> getSlotsForVendor(long vendorId, LocalDate date);

    /**
     * Rule 5.1 step 2: the slot must start on a 5-minute boundary inside the pickup window, and not before
     * "now + low estimate" rounded up to the next 5 minutes. Otherwise 400 VALIDATION_ERROR (field pickupSlotStart).
     */
    void validateRequestedSlot(Vendor vendor, Instant slotStart, int lowEstimateMinutes);

    /** now + lowEstimateMinutes, rounded up to the next 5-minute boundary. */
    Instant earliestAllowedSlot(int lowEstimateMinutes);

    /** The campus date a slot belongs to (used as the order's pickup_date). */
    LocalDate dateOf(Instant slotStart);
}
