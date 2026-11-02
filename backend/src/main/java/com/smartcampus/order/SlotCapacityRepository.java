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

    /** Rows are created lazily on first booking; two parallel first bookings must not collide. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO slot_capacity (vendor_id, slot_start, max_orders, booked_orders) " +
            "VALUES (:vendorId, :slotStart, :maxOrders, 0) ON CONFLICT (vendor_id, slot_start) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("vendorId") Long vendorId, @Param("slotStart") Instant slotStart,
                       @Param("maxOrders") int maxOrders);

    /**
     * Business Rule (5.1 & V4):
     * Atomically books a slot using a conditional statement checking capacity.
     * Returns 1 if slot booking succeeded, 0 if SLOT_FULL.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SlotCapacity s SET s.bookedOrders = s.bookedOrders + 1 " +
            "WHERE s.id.vendorId = :vendorId AND s.id.slotStart = :slotStart AND s.bookedOrders < s.maxOrders")
    int bookSlotConditional(@Param("vendorId") Long vendorId, @Param("slotStart") Instant slotStart);

    /**
     * Restores a booked slot when order is cancelled, expired, or rejected.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE SlotCapacity s SET s.bookedOrders = s.bookedOrders - 1 " +
            "WHERE s.id.vendorId = :vendorId AND s.id.slotStart = :slotStart AND s.bookedOrders > 0")
    int releaseSlot(@Param("vendorId") Long vendorId, @Param("slotStart") Instant slotStart);
}
