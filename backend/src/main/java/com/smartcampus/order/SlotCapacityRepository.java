package com.smartcampus.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SlotCapacityRepository extends JpaRepository<SlotCapacity, SlotCapacityId> {

    List<SlotCapacity> findByIdVendorIdAndIdSlotStartBetween(Long vendorId, Instant from, Instant to);

    /**
     * Business Rule (5.1 & V4):
     * Atomically books a slot using a conditional statement checking capacity.
     * Returns 1 if slot booking succeeded, 0 if SLOT_FULL.
     */
    @Modifying
    @Query("UPDATE SlotCapacity s SET s.bookedOrders = s.bookedOrders + 1 " +
            "WHERE s.id.vendorId = :vendorId AND s.id.slotStart = :slotStart AND s.bookedOrders < s.maxOrders")
    int bookSlotConditional(@Param("vendorId") Long vendorId, @Param("slotStart") Instant slotStart);

    /**
     * Restores a booked slot when order is cancelled, expired, or rejected.
     */
    @Modifying
    @Query("UPDATE SlotCapacity s SET s.bookedOrders = s.bookedOrders - 1 " +
            "WHERE s.id.vendorId = :vendorId AND s.id.slotStart = :slotStart AND s.bookedOrders > 0")
    int releaseSlot(@Param("vendorId") Long vendorId, @Param("slotStart") Instant slotStart);
}
