package com.smartcampus.order;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity tracking 5-minute pickup slot capacities per vendor.
 * Matches slot_capacity table in V4.
 */
@Entity
@Table(name = "slot_capacity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotCapacity {

    @EmbeddedId
    private SlotCapacityId id;

    @Column(name = "max_orders", nullable = false)
    private int maxOrders;

    @Column(name = "booked_orders", nullable = false)
    @Builder.Default
    private int bookedOrders = 0;
}
